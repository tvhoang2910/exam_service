package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.CreateTagRequest;
import com.exam_bank.exam_service.dto.TagDto;
import com.exam_bank.exam_service.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tags")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagDto>> listTags(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(tagService.listTags(query));
    }

    @PostMapping
    public ResponseEntity<TagDto> createTag(@RequestBody CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createTag(request.getName()));
    }
}
