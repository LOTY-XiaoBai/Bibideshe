package com.example.bishe.controller;

import com.example.bishe.common.Result;
import com.example.bishe.service.CarouselService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class CarouselController {
    private final CarouselService carousel;
    public CarouselController(CarouselService carousel) { this.carousel=carousel; }
    @GetMapping("/api/admin/commerce/carousel") public Result<?> settings() { return Result.success(carousel.settings()); }
    @PutMapping("/api/admin/commerce/carousel") public Result<?> save(@RequestBody Map<String,Object> body) { return Result.success(carousel.saveSettings(body)); }
    @PutMapping("/api/admin/commerce/campaigns/{id}/carousel") public Result<?> select(@PathVariable long id,@RequestBody Map<String,Object> body) { carousel.selectCampaign(id,body); return Result.success(null); }
    @GetMapping("/api/shop/carousel") public Result<?> feed() { return Result.success(carousel.publicCarousel()); }
}
