package org.mrqendyxz.guardianv3.Checks;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import java.util.UUID;

public interface Check {
    void handle(PacketReceiveEvent event);
    default boolean runsBeforeContext() { return false; }
    default void handleSend(PacketSendEvent event) {}
    default void cleanup(UUID id) {}
}
