package com.example.bishe.controller;

import com.example.bishe.common.*;
import com.example.bishe.dto.LoginRequest;
import com.example.bishe.service.MerchantService;
import com.example.bishe.service.MerchantCommerceService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class MerchantController {
    private final MerchantService merchants;
    private final MerchantCommerceService commerce;
    private final com.example.bishe.service.MerchantLocationService location;
    public MerchantController(MerchantService merchants,MerchantCommerceService commerce,com.example.bishe.service.MerchantLocationService location){this.merchants=merchants;this.commerce=commerce;this.location=location;}
    @GetMapping("/api/merchant/location/city") public Result<?> city(@RequestParam double lat,@RequestParam double lon){merchants.requireMerchant();return Result.success(location.city(lat,lon));}
    @GetMapping("/api/merchant/location/available") public Result<?> locationAvailable(){merchants.requireMerchant();return Result.success(Map.of("available",location.available()));}
    @GetMapping("/api/merchant/product-options") public Result<?> options(@RequestParam(defaultValue="")String keyword){return Result.success(commerce.productOptions(keyword));}
    @GetMapping("/api/merchant/product-tags") public Result<?> productTags(){return Result.success(commerce.productTags());}
    @PostMapping("/api/merchant/login") public Result<?> login(@RequestBody LoginRequest r){return Result.success(merchants.login(r));}
    @GetMapping("/api/merchant/profile") public Result<?> profile(){return Result.success(merchants.publicProfile(merchants.requireMerchant()));}
    @PutMapping("/api/merchant/profile") public Result<?> saveProfile(@RequestBody Map<String,Object>b){return Result.success(merchants.saveProfile(b));}
    @PutMapping("/api/merchant/avatar") public Result<?> avatar(@RequestBody Map<String,Object>b){return Result.success(merchants.saveAvatar(b));}
    @PutMapping("/api/merchant/cover") public Result<?> cover(@RequestBody Map<String,Object>b){return Result.success(merchants.saveCover(b));}
    @PutMapping("/api/admin/merchants/{id}/ban") public Result<?> ban(@PathVariable long id,@RequestBody Map<String,Object>b){AuthContext.requireAdmin();if(!(b.get("banned") instanceof Boolean))throw new BusinessException("请提供封禁状态");merchants.setBanned(id,Boolean.TRUE.equals(b.get("banned")),MerchantService.text(b,"reason",500,false),b.get("duration")==null?"PERMANENT":MerchantService.text(b,"duration",16,true));return Result.success(null);}
    @DeleteMapping("/api/merchant/orders/{id}") public Result<?> deleteOrder(@PathVariable long id){commerce.deleteOrder(id);return Result.success(null);}
    @GetMapping("/api/merchant/stats") public Result<?> stats(){return Result.success(commerce.stats());}
    @GetMapping("/api/merchant/{kind:products|campaigns}") public Result<?> list(@PathVariable String kind,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size){return Result.success(commerce.list(kind,false,status,page,size));}
    @PostMapping("/api/merchant/products") public Result<?> product(@RequestBody Map<String,Object>b){return Result.success(commerce.saveProduct(null,b));}
    @PutMapping("/api/merchant/products/{id}") public Result<?> editProduct(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(commerce.saveProduct(id,b));}
    @PostMapping("/api/merchant/campaigns") public Result<?> campaign(@RequestBody Map<String,Object>b){return Result.success(commerce.saveCampaign(null,b));}
    @PutMapping("/api/merchant/campaigns/{id}") public Result<?> editCampaign(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(commerce.saveCampaign(id,b));}
    @PostMapping("/api/merchant/{kind:products|campaigns}/{id}/withdraw") public Result<?> withdraw(@PathVariable String kind,@PathVariable long id){commerce.withdraw(kind,id);return Result.success(null);}
    @GetMapping("/api/merchant/orders") public Result<?> orders(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size){return Result.success(commerce.orders(page,size));}
    @PutMapping("/api/merchant/orders/{id}/logistics") public Result<?> ship(@PathVariable long id,@RequestBody Map<String,Object>b){commerce.ship(id,b);return Result.success(null);}
    @PutMapping("/api/merchant/orders/{id}/after-sale") public Result<?> afterSale(@PathVariable long id,@RequestBody Map<String,Object>b){commerce.afterSale(id,b);return Result.success(null);}
    @GetMapping("/api/merchant/evaluations") public Result<?> evaluations(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size){return Result.success(commerce.evaluations(page,size));}
    @GetMapping("/api/admin/merchants") public Result<?> merchants(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size){AuthContext.requireAdmin();return Result.success(merchants.list(page,size));}
    @PostMapping("/api/admin/merchants/invitations") public Result<?> invite(){AuthContext.requireAdmin();return Result.success(merchants.invite());}
    @GetMapping("/api/admin/commerce/counts") public Result<?> counts(){return Result.success(commerce.reviewCounts());}
    @GetMapping("/api/admin/commerce/{kind:all|products|campaigns}") public Result<?> reviewList(@PathVariable String kind,@RequestParam(required=false)String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size){return Result.success(commerce.list(kind,true,status==null?"PENDING":status,page,size));}
    @PostMapping("/api/admin/commerce/{kind:products|campaigns}/{id}/review") public Result<?> review(@PathVariable String kind,@PathVariable long id,@RequestBody Map<String,Object>b){commerce.review(kind,id,b);return Result.success(null);}
    @GetMapping("/api/shop/merchants/{id}") public Result<?> storefront(@PathVariable long id){AuthContext.requireUserId();return Result.success(commerce.storefront(id));}
    @GetMapping("/api/shop/campaigns") public Result<?> publicCampaigns(){AuthContext.requireUserId();return Result.success(commerce.publicCampaigns());}
}
