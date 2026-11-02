package com.example.bishe.controller;
import com.example.bishe.common.Result;
import com.example.bishe.service.CommunityService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/community")
public class CommunityController {
 private final CommunityService service;
 public CommunityController(CommunityService service){this.service=service;}
 @GetMapping("/posts") public Result<?> feed(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="latest") String sort,@RequestParam(defaultValue="community") String scope,@RequestParam(defaultValue="1") int page){return Result.success(service.feed(q,sort,scope,page));}
 @PostMapping("/posts") public Result<?> create(@RequestBody Map<String,Object>b){return Result.success(service.save(null,b));}
 @PutMapping("/posts/{id}") public Result<?> edit(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(service.save(id,b));}
 @GetMapping("/posts/{id}") public Result<?> detail(@PathVariable long id){return Result.success(service.detail(id));}
 @PostMapping("/posts/{id}/action") public Result<?> action(@PathVariable long id,@RequestBody Map<String,String>b){service.action(id,b.getOrDefault("action",""));return Result.success(null);}
 @PostMapping("/posts/{id}/replies") public Result<?> reply(@PathVariable long id,@RequestBody Map<String,Object>b){service.reply(id,b);return Result.success(null);}
 @DeleteMapping("/replies/{id}") public Result<?> deleteReply(@PathVariable long id){service.deleteReply(id);return Result.success(null);}
 @PostMapping("/posts/{id}/share") public Result<?> share(@PathVariable long id,@RequestBody Map<String,Object>b){service.share(id,b);return Result.success(null);}
 @GetMapping("/notices") public Result<?> notices(@RequestParam(defaultValue="1")int page){return Result.success(service.notices(page));}
 @PostMapping("/notices/read") public Result<?> read(){service.readNotices();return Result.success(null);}
 @GetMapping("/words") public Result<?> words(){return Result.success(service.wordList());}
 @PostMapping("/words") public Result<?> addWord(@RequestBody Map<String,Object>b){service.addWord(b);return Result.success(null);}
 @DeleteMapping("/words/{id}") public Result<?> deleteWord(@PathVariable long id){service.deleteWord(id);return Result.success(null);}
 @GetMapping("/mutes") public Result<?> muted(){return Result.success(service.muted());}
 @PostMapping("/unmute") public Result<?> unmute(@RequestBody Map<String,String>b){service.unmute(b.get("owner"));return Result.success(null);}
}
