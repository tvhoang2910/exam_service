package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.TagDto;
import com.exam_bank.exam_service.entity.Tag;
import com.exam_bank.exam_service.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagDto> listTags(String query) {
        List<Tag> tags;
        if (StringUtils.hasText(query)) {
            tags = tagRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
        } else {
            tags = tagRepository.findAllByOrderByNameAsc();
        }

        return tags.stream().map(this::toDto).toList();
    }

    @Transactional
    public TagDto createTag(String rawName) {
        String normalized = normalizeTagName(rawName);

        Tag existing = tagRepository.findByName(normalized).orElse(null);
        if (existing != null) {
            return toDto(existing);
        }

        Tag tag = new Tag();
        tag.setName(normalized);

        try {
            return toDto(tagRepository.save(tag));
        } catch (DataIntegrityViolationException ex) {
            return tagRepository.findByName(normalized)
                    .map(this::toDto)
                    .orElseThrow(() -> ex);
        }
    }

    public String normalizeTagName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new ResponseStatusException(BAD_REQUEST, "Tag name must not be blank");
        }

        String normalized = rawName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Tag name must not be blank");
        }

        return normalized;
    }

    public TagDto toDto(Tag tag) {
        TagDto dto = new TagDto();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        return dto;
    }
}
