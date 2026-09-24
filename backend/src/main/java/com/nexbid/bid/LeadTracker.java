package com.nexbid.bid;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * EN: Everyone who held the lead at some point during one bid and the auto bids that answered it.
 *     Whoever is not leading once it all settles has been outbid; anyone who won it straight back has not.
 * VI: Mọi người từng dẫn đầu trong một lượt trả giá và các auto bid đáp trả nó. Ai không còn dẫn khi mọi
 *     thứ ổn định là đã bị vượt giá; ai giành lại ngay thì không.
 */
final class LeadTracker {

    private final Set<UUID> contenders = new LinkedHashSet<>();

    void saw(UUID previousLeader, UUID bidder) {
        if (previousLeader != null) {
            contenders.add(previousLeader);
        }
        contenders.add(bidder);
    }

    Set<UUID> outbidBy(UUID finalLeader) {
        Set<UUID> outbid = new LinkedHashSet<>(contenders);
        outbid.remove(finalLeader);
        return outbid;
    }
}
