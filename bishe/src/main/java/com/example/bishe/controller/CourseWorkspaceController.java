package com.example.bishe.controller;

import com.example.bishe.common.*;
import com.example.bishe.dto.CourseCreateRequest;
import com.example.bishe.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/course")
public class CourseWorkspaceController {
    private final CourseWorkspaceService workspace;
    private final CourseFilesService files;
    public CourseWorkspaceController(CourseWorkspaceService workspace,CourseFilesService files){this.workspace=workspace;this.files=files;}
    @GetMapping("/{id}/overview") public Result<?> overview(@PathVariable Long id){return Result.success(workspace.overview(AuthContext.requireLogin(),id));}
    @PutMapping("/{id}/settings") public Result<?> settings(@PathVariable Long id,@RequestBody CourseCreateRequest body){return Result.success(workspace.update(AuthContext.requireTeacherId(),id,body));}
    public record Ranking(Boolean visible){}
    @PutMapping("/{id}/ranking") public Result<?> ranking(@PathVariable Long id,@RequestBody Ranking body){if(body.visible()==null)throw new BusinessException("请选择排名设置");workspace.setRanking(AuthContext.requireTeacherId(),id,body.visible());return Result.success(null);}
    @GetMapping("/{id}/applications") public Result<?> applications(@PathVariable Long id){return Result.success(workspace.applications(AuthContext.requireTeacherId(),id));}
    @PostMapping("/applications/dismiss") public Result<?> dismiss(){workspace.dismissApplications(AuthContext.requireStudentId());return Result.success(null);}
    @GetMapping("/{id}/summary") public Result<?> summary(@PathVariable Long id){return Result.success(workspace.summary(AuthContext.requireStudentId(),id));}
    @GetMapping("/{id}/materials") public Result<?> materials(@PathVariable Long id){return Result.success(files.list(AuthContext.requireLogin(),id));}
    @PostMapping("/{id}/materials") public Result<?> upload(@PathVariable Long id,@RequestParam("file") MultipartFile file){return Result.success(files.upload(AuthContext.requireTeacherId(),id,file));}
    @DeleteMapping("/{id}/materials/{materialId}") public Result<?> remove(@PathVariable Long id,@PathVariable Long materialId){files.remove(AuthContext.requireTeacherId(),id,materialId);return Result.success(null);}
    @GetMapping("/{id}/materials/{materialId}/download") public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id,@PathVariable Long materialId){
        var download=files.download(AuthContext.requireLogin(),id,materialId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").body(download.resource());
    }
}
