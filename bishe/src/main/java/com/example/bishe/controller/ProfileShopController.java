package com.example.bishe.controller;
import com.example.bishe.common.*;
import com.example.bishe.service.ProfileShopService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/extra")
public class ProfileShopController {
 private final ProfileShopService service;
 public ProfileShopController(ProfileShopService service){this.service=service;}
 @GetMapping("/profile") public Result<?> profile(){return Result.success(service.profile());}
 @PutMapping("/profile") public Result<?> save(@RequestBody Map<String,Object>b){service.saveProfile(b);return Result.success(null);}
 @GetMapping("/products/{id}") public Result<?> product(@PathVariable long id){return Result.success(service.product(id));}
 @PutMapping("/products/{id}") public Result<?> productSave(@PathVariable long id,@RequestBody Map<String,Object>b){service.productSave(id,b);return Result.success(null);}
}
