package edu.cit.carcueva.channel;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChannelOrderRepository extends JpaRepository<ChannelOrder, String> {
    List<ChannelOrder> findTop20ByDecisionIsNotNullAndDecisionReportedAtIsNullOrderByCreatedAtAsc();

    List<ChannelOrder> findTop20ByResolutionIsNotNullAndResolutionReportedAtIsNullOrderByCreatedAtAsc();

    List<ChannelOrder> findTop20ByCancelRequestedAtIsNotNullAndCancelConfirmedAtIsNullOrderByCreatedAtAsc();

    List<ChannelOrder> findByStateOrderByCreatedAtAsc(String state);

    List<ChannelOrder> findTop20ByStateAndCreatedAtBeforeOrderByCreatedAtAsc(String state, Instant before);

    long countByState(String state);

    long countByDecisionIsNotNullAndDecisionReportedAtIsNull();

    long countByResolutionIsNotNullAndResolutionReportedAtIsNull();

    long countByCancelRequestedAtIsNotNullAndCancelConfirmedAtIsNull();
}
