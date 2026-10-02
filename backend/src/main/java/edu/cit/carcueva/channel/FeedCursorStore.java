package edu.cit.carcueva.channel;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class FeedCursorStore {
    private final ChannelCursorRepository repository;

    FeedCursorStore(ChannelCursorRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public long load() {
        return repository.findById(ChannelCursor.SINGLE_ROW).map(ChannelCursor::getLastSeq).orElse(0L);
    }

    @Transactional
    public void save(long seq) {
        ChannelCursor cursor = repository.findById(ChannelCursor.SINGLE_ROW).orElseGet(() -> new ChannelCursor(seq));
        cursor.moveTo(seq);
        repository.save(cursor);
    }
}
