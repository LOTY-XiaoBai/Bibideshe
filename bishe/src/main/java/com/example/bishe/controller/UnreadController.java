package com.example.bishe.controller;
import com.example.bishe.common.Result;
import com.example.bishe.service.UnreadService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/unread")
public class UnreadController {
    private final UnreadService service;
    public UnreadController(UnreadService service){this.service=service;}
    @GetMapping public Result<?> summary(){return Result.success(service.summary());}
    @PostMapping("/read") public Result<?> read(@RequestBody ReadRequest body){service.read(body.kind(),body.targetId(),body.lastMessageId());return Result.success(null);}
    public record ReadRequest(String kind,long targetId,long lastMessageId){}
}
