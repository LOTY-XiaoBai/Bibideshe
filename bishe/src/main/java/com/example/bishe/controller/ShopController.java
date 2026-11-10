package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.Result;
import com.example.bishe.dto.AdPublishRequest;
import com.example.bishe.dto.AfterSaleReplyRequest;
import com.example.bishe.dto.AfterSaleRequest;
import com.example.bishe.dto.LogisticsUpdateRequest;
import com.example.bishe.dto.OrderCreateRequest;
import com.example.bishe.dto.OrderFeedbackRequest;
import com.example.bishe.dto.OrderVO;
import com.example.bishe.entity.Advertisement;
import com.example.bishe.entity.OrderEvaluation;
import com.example.bishe.service.MerchantLocationService;
import com.example.bishe.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 购物与广告接口(管理员 + 学生/教师)。
 */
@Tag(name = "购物与广告", description = "广告商品发布、浏览下单、物流、收货、评价与售后")
@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private final ShopService shopService;
    private final MerchantLocationService location;

    public ShopController(ShopService shopService, MerchantLocationService location) {
        this.shopService = shopService;
        this.location = location;
    }

    // ==================== 管理员 ====================

    @Operation(summary = "发布广告/商品")
    @PostMapping("/admin/ad/publish")
    public Result<Advertisement> publishAd(@RequestBody AdPublishRequest request) {
        String adminId = AuthContext.requireAdminId();
        throw new com.example.bishe.common.BusinessException(410,"请由合作方商家提交商品和广告申请，再由管理员审核");
    }

    @Operation(summary = "更新广告/商品")
    @PostMapping("/admin/ad/update")
    public Result<Advertisement> updateAd(@RequestBody AdPublishRequest request) {
        AuthContext.requireAdmin();
        throw new com.example.bishe.common.BusinessException(410,"商品和广告由所属商家编辑，管理员负责审核");
    }

    @Operation(summary = "下架广告/商品")
    @PostMapping("/admin/ad/delete")
    public Result<Void> deleteAd(@RequestParam Long adId) {
        AuthContext.requireAdmin();
        shopService.deleteAd(adId);
        return Result.success("广告已下架", null);
    }

    @Operation(summary = "分页查看全部广告(含已下架)")
    @GetMapping("/admin/ad/list")
    public Result<Map<String, Object>> listAdsAdmin(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        AuthContext.requireAdmin();
        return Result.success(shopService.listAdsForAdmin(page, size));
    }

    @Operation(summary = "分页查看全部买家订单")
    @GetMapping("/admin/order/list")
    public Result<Map<String, Object>> listOrdersAdmin(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        AuthContext.requireAdmin();
        return Result.success(shopService.listOrdersForAdmin(page, size));
    }

    @Operation(summary = "待发货订单数量")
    @GetMapping("/admin/order/pending-count")
    public Result<Map<String, Object>> pendingOrderCount() {
        AuthContext.requireAdmin();
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("count", shopService.countPendingOrders());
        return Result.success(data);
    }

    @Operation(summary = "发布物流信息")
    @PostMapping("/admin/order/logistics")
    public Result<Void> publishLogistics(@RequestBody LogisticsUpdateRequest request) {
        AuthContext.requireAdmin();
        shopService.publishLogistics(request);
        return Result.success("物流信息已发布", null);
    }

    @Operation(summary = "分页查看全部商品评价")
    @GetMapping("/admin/evaluation/list")
    public Result<Map<String, Object>> listEvaluationsAdmin(@RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        AuthContext.requireAdmin();
        return Result.success(shopService.listEvaluationsForAdmin(page, size));
    }

    @Operation(summary = "按广告查看评价")
    @GetMapping("/admin/evaluation/by-ad")
    public Result<List<OrderEvaluation>> listEvaluationsByAd(@RequestParam Long adId) {
        AuthContext.requireAdmin();
        return Result.success(shopService.listEvaluationsByAd(adId));
    }

    @Operation(summary = "处理售后申请")
    @PostMapping("/admin/order/after-sale/reply")
    public Result<Void> replyAfterSale(@RequestBody AfterSaleReplyRequest request) {
        AuthContext.requireAdmin();
        shopService.replyAfterSale(request);
        return Result.success("售后已处理", null);
    }

    // ==================== 学生/教师 ====================

    @Operation(summary = "浏览在售广告商品")
    @GetMapping("/ad/list")
    public Result<List<Advertisement>> listAds(@RequestParam(defaultValue="") String keyword,
                                               @RequestParam(defaultValue="") String minPrice,
                                               @RequestParam(defaultValue="") String maxPrice,
                                               @RequestParam(defaultValue="") String tags,
                                               @RequestParam(defaultValue="") String city) {
        AuthContext.requireUserId();
        return Result.success(shopService.listActiveAds(keyword, minPrice, maxPrice, tags, city));
    }

    @GetMapping("/catalog-options")
    public Result<Map<String, Object>> catalogOptions() {
        AuthContext.requireUserId();
        return Result.success(shopService.catalogOptions());
    }

    @GetMapping("/location/available")
    public Result<Map<String, Boolean>> locationAvailable() {
        AuthContext.requireUserId();
        return Result.success(Map.of("available", location.available()));
    }

    @GetMapping("/location/city")
    public Result<Map<String, String>> locateCity(@RequestParam(required=false) Double lat,
                                                  @RequestParam(required=false) Double lon) {
        AuthContext.requireUserId();
        if (lat == null || lon == null) {
            throw new BusinessException("缺少定位坐标，请手动选择发货城市");
        }
        return Result.success(Map.of("city", shopService.catalogCity(location.city(lat, lon).get("city"))));
    }

    @Operation(summary = "广告/商品详情")
    @GetMapping("/ad/{adId}")
    public Result<Advertisement> adDetail(@PathVariable Long adId) {
        AuthContext.requireUserId();
        return Result.success(shopService.getAdDetail(adId));
    }

    @Operation(summary = "下单购买")
    @PostMapping("/order/create")
    public Result<OrderVO> createOrder(@RequestBody OrderCreateRequest request) {
        Long buyerId = AuthContext.requireUserId();
        return Result.success("下单成功", shopService.createOrder(buyerId, request));
    }

    @Operation(summary = "我的订单列表")
    @GetMapping("/order/mine")
    public Result<List<OrderVO>> myOrders() {
        Long buyerId = AuthContext.requireUserId();
        return Result.success(shopService.listMyOrders(buyerId));
    }
    @org.springframework.web.bind.annotation.DeleteMapping("/order/{orderId}")
    public Result<Void> deleteOrder(@PathVariable Long orderId){shopService.deleteMyOrder(AuthContext.requireUserId(),orderId);return Result.success("订单记录已删除",null);}

    @Operation(summary = "订单详情(含物流)")
    @GetMapping("/order/{orderId}")
    public Result<OrderVO> orderDetail(@PathVariable Long orderId) {
        Long buyerId = AuthContext.requireUserId();
        return Result.success(shopService.getMyOrder(buyerId, orderId));
    }

    @Operation(summary = "确认收货")
    @PostMapping("/order/confirm")
    public Result<Void> confirmReceived(@RequestParam Long orderId) {
        Long buyerId = AuthContext.requireUserId();
        shopService.confirmReceived(buyerId, orderId);
        return Result.success("已确认收货", null);
    }

    @Operation(summary = "提交评价反馈")
    @PostMapping("/order/feedback")
    public Result<OrderEvaluation> feedback(@RequestBody OrderFeedbackRequest request) {
        Long buyerId = AuthContext.requireUserId();
        return Result.success("评价成功", shopService.submitFeedback(buyerId, request));
    }

    @Operation(summary = "提交售后申请")
    @PostMapping("/order/after-sale")
    public Result<Void> afterSale(@RequestBody AfterSaleRequest request) {
        Long buyerId = AuthContext.requireUserId();
        shopService.submitAfterSale(buyerId, request);
        return Result.success("售后申请已提交", null);
    }
}
