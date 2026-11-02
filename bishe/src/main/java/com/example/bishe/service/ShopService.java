package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.dto.AdPublishRequest;
import com.example.bishe.dto.AfterSaleReplyRequest;
import com.example.bishe.dto.AfterSaleRequest;
import com.example.bishe.dto.LogisticsUpdateRequest;
import com.example.bishe.dto.OrderCreateRequest;
import com.example.bishe.dto.OrderFeedbackRequest;
import com.example.bishe.dto.OrderVO;
import com.example.bishe.entity.Advertisement;
import com.example.bishe.entity.OrderEvaluation;
import com.example.bishe.entity.OrderRecord;
import com.example.bishe.mapper.AdvertisementMapper;
import com.example.bishe.mapper.OrderEvaluationMapper;
import com.example.bishe.mapper.OrderRecordMapper;
import com.example.bishe.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物与广告服务: 管理员发布商品/物流, 师生浏览下单、收货、评价与售后。
 */
@Service
public class ShopService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SHIPPED = 1;
    public static final int STATUS_RECEIVED = 2;

    public static final int AFTER_SALE_NONE = 0;
    public static final int AFTER_SALE_PENDING = 1;
    public static final int AFTER_SALE_DONE = 2;

    private static final int MAX_ID_RETRY = 20;

    private final AdvertisementMapper advertisementMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final OrderEvaluationMapper orderEvaluationMapper;
    private final SocialUserService socialUserService;

    public ShopService(AdvertisementMapper advertisementMapper,
                       OrderRecordMapper orderRecordMapper,
                       OrderEvaluationMapper orderEvaluationMapper,
                       SocialUserService socialUserService) {
        this.advertisementMapper = advertisementMapper;
        this.orderRecordMapper = orderRecordMapper;
        this.orderEvaluationMapper = orderEvaluationMapper;
        this.socialUserService = socialUserService;
    }

    // ==================== 管理员: 广告/商品 ====================

    @Transactional
    public Advertisement publishAd(String adminId, AdPublishRequest request) {
        validateAdRequest(request, false);
        Advertisement ad = new Advertisement();
        ad.setTitle(request.getTitle().trim());
        ad.setContent(request.getContent());
        ad.setImageUrl(request.getImageUrl());
        ad.setPrice(request.getPrice() == null ? BigDecimal.ZERO : request.getPrice());
        ad.setPublisherId(adminId);
        ad.setScore(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP));
        ad.setIsDeleted(0);
        advertisementMapper.insert(ad);
        return advertisementMapper.findById(ad.getAdId());
    }

    @Transactional
    public Advertisement updateAd(AdPublishRequest request) {
        if (request.getAdId() == null) {
            throw new BusinessException("广告ID不能为空");
        }
        Advertisement existing = requireActiveAd(request.getAdId());
        validateAdRequest(request, true);
        existing.setTitle(request.getTitle().trim());
        existing.setContent(request.getContent());
        existing.setImageUrl(request.getImageUrl());
        if (request.getPrice() != null) {
            existing.setPrice(request.getPrice());
        }
        advertisementMapper.update(existing);
        return advertisementMapper.findById(existing.getAdId());
    }

    @Transactional
    public void deleteAd(Long adId) {
        requireAd(adId);
        advertisementMapper.softDelete(adId);
    }

    public Map<String, Object> listAdsForAdmin(int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        int offset = (page - 1) * size;
        Map<String, Object> result = new HashMap<>();
        result.put("total", advertisementMapper.countAll());
        result.put("page", page);
        result.put("size", size);
        result.put("list", advertisementMapper.findAllForAdmin(offset, size));
        return result;
    }

    // ==================== 师生: 浏览广告 ====================

    public List<Advertisement> listActiveAds() {
        return advertisementMapper.findAllActive();
    }

    public Advertisement getAdDetail(Long adId) {
        return requireActiveAd(adId);
    }

    // ==================== 师生: 下单 / 物流 / 收货 / 评价 / 售后 ====================

    @Transactional
    public OrderVO createOrder(Long buyerId, OrderCreateRequest request) {
        if (request == null || request.getAdId() == null) {
            throw new BusinessException("广告ID不能为空");
        }
        socialUserService.requireProfile(buyerId);
        Advertisement ad = requireActiveAd(request.getAdId());

        OrderRecord order = new OrderRecord();
        order.setOrderId(generateUniqueOrderId());
        order.setAdId(ad.getAdId());
        order.setBuyerId(buyerId);
        order.setIsReceived(0);
        order.setStatus(STATUS_PENDING);
        order.setAfterSaleStatus(AFTER_SALE_NONE);
        orderRecordMapper.insert(order);
        return toOrderVO(orderRecordMapper.findById(order.getOrderId()));
    }

    public List<OrderVO> listMyOrders(Long buyerId) {
        List<OrderRecord> orders = orderRecordMapper.findByBuyerId(buyerId);
        List<OrderVO> result = new ArrayList<>();
        for (OrderRecord order : orders) {
            result.add(toOrderVO(order));
        }
        return result;
    }

    public OrderVO getMyOrder(Long buyerId, Long orderId) {
        OrderRecord order = requireOrder(orderId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BusinessException("无权查看该订单");
        }
        return toOrderVO(order);
    }

    @Transactional
    public void confirmReceived(Long buyerId, Long orderId) {
        OrderRecord order = requireOrder(orderId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BusinessException("无权操作该订单");
        }
        if (order.getStatus() == null || order.getStatus() != STATUS_SHIPPED) {
            throw new BusinessException("仅已发货订单可确认收货");
        }
        if (order.getIsReceived() != null && order.getIsReceived() == 1) {
            throw new BusinessException("已确认收货, 请勿重复操作");
        }
        orderRecordMapper.confirmReceived(orderId);
    }

    @Transactional
    public OrderEvaluation submitFeedback(Long buyerId, OrderFeedbackRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw new BusinessException("订单ID不能为空");
        }
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new BusinessException("评价星级须为1-5");
        }
        OrderRecord order = requireOrder(request.getOrderId());
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BusinessException("无权评价该订单");
        }
        if (order.getIsReceived() == null || order.getIsReceived() != 1) {
            throw new BusinessException("请先确认收货后再评价");
        }
        if (orderEvaluationMapper.findByOrderId(order.getOrderId()) != null) {
            throw new BusinessException("该订单已评价");
        }

        OrderEvaluation evaluation = new OrderEvaluation();
        evaluation.setOrderId(order.getOrderId());
        evaluation.setBuyerId(buyerId);
        evaluation.setRating(request.getRating());
        evaluation.setFeedbackContent(request.getFeedbackContent());
        orderEvaluationMapper.insert(evaluation);

        refreshAdScore(order.getAdId());
        return orderEvaluationMapper.findById(evaluation.getEvaluationId());
    }

    @Transactional
    public void submitAfterSale(Long buyerId, AfterSaleRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw new BusinessException("订单ID不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException("售后内容不能为空");
        }
        OrderRecord order = requireOrder(request.getOrderId());
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BusinessException("无权操作该订单");
        }
        if (order.getIsReceived() == null || order.getIsReceived() != 1) {
            throw new BusinessException("请先确认收货后再申请售后");
        }
        if (order.getAfterSaleStatus() != null && order.getAfterSaleStatus() == AFTER_SALE_PENDING) {
            throw new BusinessException("已有进行中的售后申请");
        }
        orderRecordMapper.submitAfterSale(order.getOrderId(), request.getContent().trim());
    }

    // ==================== 管理员: 订单 / 物流 / 评价 / 售后 ====================

    public Map<String, Object> listOrdersForAdmin(int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        int offset = (page - 1) * size;
        List<OrderRecord> orders = orderRecordMapper.findAll(offset, size);
        List<OrderVO> list = new ArrayList<>();
        for (OrderRecord order : orders) {
            list.add(toOrderVO(order));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", orderRecordMapper.countAll());
        result.put("page", page);
        result.put("size", size);
        result.put("list", list);
        return result;
    }

    /** 待发货订单数量，供管理员侧栏提醒 */
    public int countPendingOrders() {
        return orderRecordMapper.countPending();
    }

    @Transactional
    public void publishLogistics(LogisticsUpdateRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw new BusinessException("订单ID不能为空");
        }
        if (!StringUtils.hasText(request.getLogisticsInfo())) {
            throw new BusinessException("物流信息不能为空");
        }
        OrderRecord order = requireOrder(request.getOrderId());
        if (order.getIsReceived() != null && order.getIsReceived() == 1) {
            throw new BusinessException("订单已收货, 无法修改物流");
        }
        orderRecordMapper.updateLogistics(order.getOrderId(), request.getLogisticsInfo().trim());
    }

    public Map<String, Object> listEvaluationsForAdmin(int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        int offset = (page - 1) * size;
        Map<String, Object> result = new HashMap<>();
        result.put("total", orderEvaluationMapper.countAll());
        result.put("page", page);
        result.put("size", size);
        result.put("list", orderEvaluationMapper.findAll(offset, size));
        return result;
    }

    public List<OrderEvaluation> listEvaluationsByAd(Long adId) {
        requireAd(adId);
        return orderEvaluationMapper.findByAdId(adId);
    }

    @Transactional
    public void replyAfterSale(AfterSaleReplyRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw new BusinessException("订单ID不能为空");
        }
        if (!StringUtils.hasText(request.getReply())) {
            throw new BusinessException("售后回复不能为空");
        }
        OrderRecord order = requireOrder(request.getOrderId());
        if (order.getAfterSaleStatus() == null || order.getAfterSaleStatus() != AFTER_SALE_PENDING) {
            throw new BusinessException("该订单没有待处理的售后申请");
        }
        orderRecordMapper.replyAfterSale(order.getOrderId(), request.getReply().trim());
    }

    // ==================== 内部 ====================

    private void refreshAdScore(Long adId) {
        BigDecimal avg = orderEvaluationMapper.avgRatingByAdId(adId);
        if (avg == null) {
            avg = BigDecimal.ZERO;
        }
        advertisementMapper.updateScore(adId, avg.setScale(1, RoundingMode.HALF_UP));
    }

    private void validateAdRequest(AdPublishRequest request, boolean allowPartial) {
        if (request == null) {
            throw new BusinessException("请求不能为空");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException("广告标题不能为空");
        }
        if (!allowPartial && !StringUtils.hasText(request.getContent())) {
            throw new BusinessException("广告内容不能为空");
        }
        if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("价格不能为负数");
        }
    }

    private Advertisement requireAd(Long adId) {
        if (adId == null) {
            throw new BusinessException("广告ID不能为空");
        }
        Advertisement ad = advertisementMapper.findById(adId);
        if (ad == null) {
            throw new BusinessException("广告不存在");
        }
        return ad;
    }

    private Advertisement requireActiveAd(Long adId) {
        Advertisement ad = requireAd(adId);
        if (ad.getIsDeleted() != null && ad.getIsDeleted() == 1) {
            throw new BusinessException("该商品已下架");
        }
        return ad;
    }

    private OrderRecord requireOrder(Long orderId) {
        if (orderId == null) {
            throw new BusinessException("订单ID不能为空");
        }
        OrderRecord order = orderRecordMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    private OrderVO toOrderVO(OrderRecord order) {
        OrderVO vo = new OrderVO();
        vo.setOrder(order);
        vo.setAdvertisement(advertisementMapper.findById(order.getAdId()));
        vo.setBuyer(socialUserService.findProfile(order.getBuyerId()));
        vo.setEvaluation(orderEvaluationMapper.findByOrderId(order.getOrderId()));
        return vo;
    }

    private Long generateUniqueOrderId() {
        for (int i = 0; i < MAX_ID_RETRY; i++) {
            Long id = IdGenerator.generateOrderId();
            if (orderRecordMapper.findById(id) == null) {
                return id;
            }
        }
        throw new BusinessException("订单ID生成失败, 请重试");
    }

    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size < 1 || size > 100) {
            return 20;
        }
        return size;
    }
}
