package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.reservation.infrastructure.persistence.entity.RoomTypeInventoryJpaEntity;
import com.reservation.reservation.infrastructure.persistence.entity.RoomTypeInventoryJpaEntity.InventoryPk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RoomTypeInventoryJpaRepository extends JpaRepository<RoomTypeInventoryJpaEntity, InventoryPk> {

    @Query("""
        select i from RoomTypeInventoryJpaEntity i
        where i.id.hotelId = :hotelId
          and i.id.roomTypeId = :roomTypeId
          and i.id.stayDate between :fromDate and :toDate
        order by i.id.stayDate asc
        """)
    List<RoomTypeInventoryJpaEntity> findRange(@Param("hotelId") UUID hotelId,
                                                @Param("roomTypeId") UUID roomTypeId,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate);

    @Query("""
        select i from RoomTypeInventoryJpaEntity i
        where i.id.hotelId = :hotelId
          and i.id.stayDate between :fromDate and :toDate
        order by i.id.roomTypeId asc, i.id.stayDate asc
        """)
    List<RoomTypeInventoryJpaEntity> findRangeByHotel(@Param("hotelId") UUID hotelId,
                                                       @Param("fromDate") LocalDate fromDate,
                                                       @Param("toDate") LocalDate toDate);
}
