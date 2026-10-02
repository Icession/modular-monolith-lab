package edu.cit.carcueva.channel;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChannelController {
    private final MarketplaceChannel channel;

    ChannelController(MarketplaceChannel channel) {
        this.channel = channel;
    }

    @GetMapping("/api/channel/status")
    public ChannelStatus status() {
        return channel.status();
    }
}
