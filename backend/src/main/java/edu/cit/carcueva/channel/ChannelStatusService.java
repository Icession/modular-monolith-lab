package edu.cit.carcueva.channel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import edu.cit.carcueva.AppInstance;

@Service
class ChannelStatusService implements MarketplaceChannel {
    private static final List<String> STATES = List.of(
            ChannelOrder.NEW, ChannelOrder.ACCEPTED, ChannelOrder.REJECTED, ChannelOrder.BACKORDERED,
            ChannelOrder.BACKORDER_FILLED, ChannelOrder.BACKORDER_CANCELLED, ChannelOrder.CUSTOMER_CANCELLED);

    private final AppInstance appInstance;
    private final ChannelState state;
    private final FeedCursorStore cursorStore;
    private final ChannelOrderRepository repository;

    ChannelStatusService(AppInstance appInstance, ChannelState state, FeedCursorStore cursorStore,
                         ChannelOrderRepository repository) {
        this.appInstance = appInstance;
        this.state = state;
        this.cursorStore = cursorStore;
        this.repository = repository;
    }

    @Override
    public ChannelStatus status() {
        Map<String, Long> byState = new LinkedHashMap<>();
        for (String s : STATES) {
            byState.put(s, repository.countByState(s));
        }
        return new ChannelStatus(
                appInstance.id(),
                state.isLive(),
                state.lastHeartbeatAt(),
                state.lastFeedReadAt(),
                cursorStore.load(),
                byState,
                repository.countByDecisionIsNotNullAndDecisionReportedAtIsNull(),
                repository.countByCancelRequestedAtIsNotNullAndCancelConfirmedAtIsNull(),
                repository.countByResolutionIsNotNullAndResolutionReportedAtIsNull());
    }
}
