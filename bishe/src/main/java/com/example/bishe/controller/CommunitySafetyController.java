package com.example.bishe.controller;
import com.example.bishe.common.Result;
import com.example.bishe.service.CommunitySafetyService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/community")
public class CommunitySafetyController {
 private final CommunitySafetyService service;
 public CommunitySafetyController(CommunitySafetyService service){this.service=service;}
 @PostMapping("/posts/{id}/block") public Result<?> block(@PathVariable long id,@RequestBody Map<String,String>b){service.block(id,b.getOrDefault("kind",""));return Result.success(null);}
 @PostMapping("/posts/{id}/report") public Result<?> report(@PathVariable long id,@RequestBody Map<String,String>b){service.report(id,b);return Result.success(null);}
 @GetMapping("/blocks") public Result<?> blocks(){return Result.success(service.blocks());}
 @DeleteMapping("/blocks/{id}") public Result<?> unblock(@PathVariable long id){service.unblock(id);return Result.success(null);}
 @GetMapping("/reports") public Result<?> reports(@RequestParam(defaultValue="PENDING")String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.reports(status,page,size));}
 @PostMapping("/reports/{id}/resolve") public Result<?> resolve(@PathVariable long id,@RequestBody Map<String,String>b){service.resolve(id,b);return Result.success(null);}
 @GetMapping("/appeals") public Result<?> appeals(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.appealPage(com.example.bishe.common.UserRole.ADMIN.equals(com.example.bishe.common.AuthContext.requireLogin().getRole()),page,size));}
 @PostMapping("/operations/{id}/appeal") public Result<?> appealOperation(@PathVariable long id,@RequestBody Map<String,String>b){service.appealOperation(id,b.get("content"));return Result.success(null);}
 @GetMapping("/appealable") public Result<?> appealable(){return Result.success(service.appealable());}
 @PostMapping("/reports/{id}/appeal") public Result<?> appeal(@PathVariable long id,@RequestBody Map<String,String>b){service.appeal(id,b.get("content"));return Result.success(null);}
 @PostMapping("/appeals/{id}/resolve") public Result<?> resolveAppeal(@PathVariable long id,@RequestBody Map<String,String>b){service.resolveAppeal(id,b);return Result.success(null);}
}

