package com.tars.service;

import com.tars.model.Timeline;
import com.tars.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TimelineService {

    private final TimelineRepository timelineRepository;

    public List<Timeline> getAllTimelines() {
        return timelineRepository.findAll();
    }
}