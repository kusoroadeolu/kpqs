package io.github.kusoroadeolu.cbs;

public class ContentionCounter {
    public long offersNearHead;
    public long failedOffers;
    public long pollCases;
    public long failedPollCas;

    public void reset() {
        failedOffers = 0;
        offersNearHead = 0;
        failedPollCas = 0;
        pollCases = 0;
    }
}
