package com.sainisagar.auditlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chain_state")
public class ChainState {

    @Id
    @Column(name = "chain_name", length = 50)
    private String chainName;

    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    protected ChainState() {
    }

    public Long getLastSequence() {
        return lastSequence;
    }

    public String getLastHash() {
        return lastHash;
    }

    public void advance(long sequence, String hash) {
        this.lastSequence = sequence;
        this.lastHash = hash;
    }
}
