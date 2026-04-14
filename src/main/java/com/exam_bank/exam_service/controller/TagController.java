package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.CreateTagRequest;
import com.exam_bank.exam_service.dto.TagDto;
import com.exam_bank.exam_service.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
@Slf4j
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagDto>> listTags(@RequestParam(required = false) String query) {
        List<TagDto> tags = tagService.listTags(query);
        log.info("listTags: query={}, count={}", query, tags.size());
        return ResponseEntity.ok(tags);
    }

    @PostMapping
    public ResponseEntity<TagDto> createTag(@RequestBody CreateTagRequest request) {
        TagDto tag = tagService.createTag(request.getName());
        log.info("createTag: name={}, tagId={}", request.getName(), tag.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(tag);
    }
}
