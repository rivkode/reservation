package com.reservation.hotel.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 도메인 VO 의 불변식(공백 거부 · 길이 · 범위) 을 한 번에 검증한다.
 * VO 마다 파일을 분리하지 않고 묶은 이유는 각 VO 규칙이 한두 줄이라 같은 파일에
 * 두는 편이 문맥 파악에 유리하기 때문.
 */
class ValueObjectsTest {

    @Nested
    @DisplayName("HotelName")
    class HotelNameTest {

        @Test
        @DisplayName("공백만 있으면 거부")
        void rejectsBlank() {
            assertThatThrownBy(() -> new HotelName("   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("앞뒤 공백은 trim")
        void trimsValue() {
            assertThat(new HotelName("  A  ").value()).isEqualTo("A");
        }

        @Test
        @DisplayName("200자 초과 거부")
        void rejectsTooLong() {
            String longStr = "a".repeat(HotelName.MAX_LENGTH + 1);
            assertThatThrownBy(() -> new HotelName(longStr))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("HotelAddress")
    class HotelAddressTest {

        @Test
        @DisplayName("3 필드 모두 비어 있으면 거부")
        void rejectsAnyBlank() {
            assertThatThrownBy(() -> new HotelAddress("", "Seoul", "KR"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HotelAddress("S", "", "KR"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HotelAddress("S", "Seoul", ""))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("StarRating")
    class StarRatingTest {

        @Test
        @DisplayName("1~5 범위 밖 거부")
        void rejectsOutOfRange() {
            assertThatThrownBy(() -> new StarRating(0))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new StarRating(6))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("경계값 1, 5 허용")
        void acceptsBoundary() {
            assertThat(new StarRating(1).value()).isEqualTo(1);
            assertThat(new StarRating(5).value()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("MaxOccupancy")
    class MaxOccupancyTest {

        @Test
        @DisplayName("0 이하 · 10 초과 거부")
        void rejectsOutOfRange() {
            assertThatThrownBy(() -> new MaxOccupancy(0))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new MaxOccupancy(11))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Floor")
    class FloorTest {

        @Test
        @DisplayName("지하층(음수) 허용")
        void allowsNegative() {
            assertThat(new Floor(-1).value()).isEqualTo(-1);
        }

        @Test
        @DisplayName("상한 초과 거부")
        void rejectsAboveMax() {
            assertThatThrownBy(() -> new Floor(Floor.MAX + 1))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("RoomNumber")
    class RoomNumberTest {

        @Test
        @DisplayName("공백만 있으면 거부")
        void rejectsBlank() {
            assertThatThrownBy(() -> new RoomNumber("   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("16자 초과 거부")
        void rejectsTooLong() {
            String longStr = "a".repeat(RoomNumber.MAX_LENGTH + 1);
            assertThatThrownBy(() -> new RoomNumber(longStr))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("RoomTypeName")
    class RoomTypeNameTest {

        @Test
        @DisplayName("100자 초과 거부")
        void rejectsTooLong() {
            String longStr = "a".repeat(RoomTypeName.MAX_LENGTH + 1);
            assertThatThrownBy(() -> new RoomTypeName(longStr))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("HotelId / RoomTypeId / RoomId")
    class IdentifierTest {

        @Test
        @DisplayName("newId 는 매번 다른 값")
        void newIdIsUnique() {
            assertThat(HotelId.newId()).isNotEqualTo(HotelId.newId());
            assertThat(RoomTypeId.newId()).isNotEqualTo(RoomTypeId.newId());
            assertThat(RoomId.newId()).isNotEqualTo(RoomId.newId());
        }

        @Test
        @DisplayName("of(String) 로 왕복 변환 가능")
        void roundTripString() {
            HotelId original = HotelId.newId();
            HotelId restored = HotelId.of(original.asString());
            assertThat(restored).isEqualTo(original);
        }
    }
}
