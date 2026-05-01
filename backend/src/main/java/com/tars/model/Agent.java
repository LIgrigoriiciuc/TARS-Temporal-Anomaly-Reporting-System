package com.tars.model;

@Entity
@Data @EqualsAndHashCode(callSuper = true)
public class Agent extends User {
    private Integer monthlyReportCount = 0;
    // Subscription and TimelineAccess in 2nd iteration
}
