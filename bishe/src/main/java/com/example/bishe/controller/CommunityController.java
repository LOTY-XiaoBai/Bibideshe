package com.example.bishe.controller;
import com.example.bishe.common.Result;
import com.example.bishe.service.CommunityService;
import com.example.bishe.service.CommunityShareService;
import com.example.bishe.service.AdminOperationService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/community")
public class CommunityController {
 private final CommunityService service;
 private final CommunityShareService sharing;
 private final AdminOperationService operations;
 public CommunityController(CommunityService service,CommunityShareService sharing,AdminOperationService operations){this.service=service;this.sharing=sharing;this.operations=operations;}
 @GetMapping("/posts") public Result<?> feed(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="latest") String sort,@RequestParam(defaultValue="community") String scope,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="-1")int offset,@RequestParam(defaultValue="20")int size){return Result.success(offset<0?service.feed(q,sort,scope,page):service.feed(q,sort,scope,page,offset,size));}
 @PostMapping("/posts") public Result<?> create(@RequestBody Map<String,Object>b){return Result.success(service.save(null,b));}
 @PutMapping("/posts/{id}") public Result<?> edit(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(service.save(id,b));}
 @GetMapping("/posts/{id}") public Result<?> detail(@PathVariable long id){return Result.success(service.detail(id));}
 @PostMapping("/posts/{id}/action") public Result<?> action(@PathVariable long id,@RequestBody Map<String,String>b){service.action(id,b.getOrDefault("action",""));return Result.success(null);}
 @PostMapping("/posts/{id}/replies") public Result<?> reply(@PathVariable long id,@RequestBody Map<String,Object>b){service.reply(id,b);return Result.success(null);}
 @DeleteMapping("/replies/{id}") public Result<?> deleteReply(@PathVariable long id){service.deleteReply(id);return Result.success(null);}
 @PostMapping("/replies/{id}/like") public Result<?> likeReply(@PathVariable long id){service.likeReply(id);return Result.success(null);}
 @PostMapping("/posts/{id}/share") public Result<?> share(@PathVariable long id,@RequestBody Map<String,Object>b){service.share(id,b);return Result.success(null);}
 @GetMapping("/share-targets") public Result<?> shareTargets(){return Result.success(sharing.targets());}
 @PostMapping("/posts/{id}/share-batch") public Result<?> shareBatch(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(sharing.share(id,b));}
 @GetMapping("/operations") public Result<?> operations(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(operations.list(q,page,size));}
 @PostMapping("/moderate-user") public Result<?> moderateUser(@RequestBody Map<String,Object>b){service.moderateUser(b);return Result.success(null);}
 @GetMapping("/notices") public Result<?> notices(@RequestParam(defaultValue="1")int page){return Result.success(service.noticePage(page));}
 @PostMapping("/notices/read") public Result<?> read(){service.readNotices();return Result.success(null);}
 @PostMapping("/notices/{id}/read") public Result<?> readNotice(@PathVariable long id){service.readNotice(id);return Result.success(null);}
 @GetMapping("/words") public Result<?> words(){return Result.success(service.wordList());}
 @PostMapping("/words") public Result<?> addWord(@RequestBody Map<String,Object>b){service.addWord(b);return Result.success(null);}
 @DeleteMapping("/words/{id}") public Result<?> deleteWord(@PathVariable long id){service.deleteWord(id);return Result.success(null);}
 @GetMapping("/mutes") public Result<?> muted(@RequestParam(defaultValue="ALL")String role,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.mutedPage(role,page,size));}
 @PostMapping("/unmute") public Result<?> unmute(@RequestBody Map<String,String>b){service.unmute(b.get("owner"));return Result.success(null);}
 @PostMapping("/clear-warnings") public Result<?> clearWarnings(@RequestBody Map<String,String>b){service.clearWarnings(b.get("owner"));return Result.success(null);}
}
