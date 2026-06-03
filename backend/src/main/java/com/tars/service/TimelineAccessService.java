package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.Timeline;
import com.tars.model.TimelineAccess;
import com.tars.model.dto.TimelineDTO;
import com.tars.model.enums.PlanType;
import com.tars.repository.TimelineAccessRepository;
import com.tars.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineAccessService {

    private final TimelineAccessRepository timelineAccessRepository;
    private final TimelineRepository timelineRepository;
    private final SubscriptionService subscriptionService;

    /**
     * Returns all timelines with accessible=true/false flag.
     * Used for report form dropdown and graph locked lanes.
     * ENTERPRISE agents see all as accessible.
     */
    public List<TimelineDTO> getAllTimelinesForAgent(Agent agent) {
        PlanType plan = subscriptionService.getOrCreateFreeSubscription(agent).getPlan();

        // Get agent's currently active timeline IDs
        Set<Long> accessibleIds = timelineAccessRepository.findByAgentIdAndActiveTrue(agent.getId())
                .stream()
                .map(ta -> ta.getTimeline().getId())
                .collect(Collectors.toSet());

        return timelineRepository.findAll()
                .stream()
                .map(t -> TimelineDTO.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .description(t.getDescription())
                        // ENTERPRISE sees everything, others see only their assigned timelines
                        .accessible(plan == PlanType.ENTERPRISE || accessibleIds.contains(t.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Agent selects a timeline to add to their plan.
     * Checks plan limit before granting access.
     */
    @Transactional
    public TimelineDTO addTimeline(Agent agent, Long timelineId) {
        Timeline timeline = timelineRepository.findById(timelineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeline not found"));

        PlanType plan = subscriptionService.getOrCreateFreeSubscription(agent).getPlan();

        // ENTERPRISE has no limit
        if (plan != PlanType.ENTERPRISE) {
            int limit = subscriptionService.getTimelineLimit(plan);
            long current = timelineAccessRepository.countByAgentIdAndActiveTrue(agent.getId());
            if (current >= limit) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Timeline limit reached for your plan (" + limit + "). Upgrade to add more.");
            }
        }

        // Check if already exists (reactivate if inactive)
        TimelineAccess access = timelineAccessRepository
                .findByAgentIdAndTimelineId(agent.getId(), timelineId)
                .orElse(null);

        if (access != null) {
            if (access.isActive()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Timeline already accessible");
            }
            access.setActive(true);
        } else {
            access = TimelineAccess.builder()
                    .agent(agent)
                    .timeline(timeline)
                    .active(true)
                    .build();
        }

        timelineAccessRepository.save(access);
        log.info("TimelineAccessService: agent {} granted access to timeline {}", agent.getId(), timelineId);

        return TimelineDTO.builder()
                .id(timeline.getId())
                .name(timeline.getName())
                .description(timeline.getDescription())
                .accessible(true)
                .build();
    }

    /**
     * Called on upgradeM PRO gets 5 slots, ENTERPRISE gets all.
     * Existing active timelines are preserved.
     */
    @Transactional
    public void onUpgrade(Agent agent, PlanType newPlan) {
        if (newPlan == PlanType.ENTERPRISE) {
            // Grant access to all timelines automatically
            List<Timeline> all = timelineRepository.findAll();
            for (Timeline t : all) {
                timelineAccessRepository.findByAgentIdAndTimelineId(agent.getId(), t.getId())
                        .ifPresentOrElse(
                                existing -> {
                                    existing.setActive(true);
                                    timelineAccessRepository.save(existing);
                                },
                                () -> timelineAccessRepository.save(
                                        TimelineAccess.builder()
                                                .agent(agent)
                                                .timeline(t)
                                                .active(true)
                                                .build()
                                )
                        );
            }
            log.info("TimelineAccessService: ENTERPRISE upgrade — all timelines granted to agent {}", agent.getId());
        }
        // PRO — agent manually picks up to 5, existing ones preserved
    }

    /**
     * Called on downgrade to FREE, keeps most recently granted timeline, deactivates rest.
     */
    @Transactional
    public void onDowngradeToFree(Agent agent) {
        List<TimelineAccess> active = timelineAccessRepository.findByAgentIdAndActiveTrue(agent.getId());
        if (active.size() <= 1) return;

        // Keep the first one (oldest granted), deactivate the rest
        active.stream().skip(1).forEach(ta -> {
            ta.setActive(false);
            timelineAccessRepository.save(ta);
        });
        log.info("TimelineAccessService: downgrade to FREE — deactivated {} timelines for agent {}",
                active.size() - 1, agent.getId());
    }

    /**
     * Validates that agent has access to a specific timeline.
     * Called before report submission.
     */
    public void enforceTimelineAccess(Agent agent, Long timelineId) {
        PlanType plan = subscriptionService.getOrCreateFreeSubscription(agent).getPlan();
        if (plan == PlanType.ENTERPRISE) return;

        boolean hasAccess = timelineAccessRepository
                .findByAgentIdAndTimelineId(agent.getId(), timelineId)
                .map(TimelineAccess::isActive)
                .orElse(false);

        if (!hasAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You don't have access to this timeline under your current plan");
        }
    }
}