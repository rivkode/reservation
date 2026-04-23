package com.reservation.reservation.domain.repository;

import com.reservation.reservation.domain.model.RoomAssignment;
import com.reservation.reservation.domain.model.RoomId;

import java.util.Optional;

/**
 * RoomAssignment Aggregate 영속화 계약 — roomId 기반 단건 CRUD 만 필요.
 *
 * <p>Tombstone 정책은 Inventory 와 Assignment 가 다르다:
 * <ul>
 *   <li>{@code RoomTypeInventory}: {@code totalRooms=0} row 도 삭제하지 않고 유지
 *       (감사 · 재전송 내성).</li>
 *   <li>{@code RoomAssignment}: {@link #deleteByRoomId} 로 hard delete. 해당 Room 자체가
 *       hotel-service 에서 제거된 상태이므로 매핑을 보존할 의미가 없고, 같은 {@code roomId}
 *       가 재사용될 가능성도 없다(UUID v7).</li>
 * </ul>
 */
public interface RoomAssignmentRepository {

    Optional<RoomAssignment> findByRoomId(RoomId roomId);

    void save(RoomAssignment assignment);

    void deleteByRoomId(RoomId roomId);
}
