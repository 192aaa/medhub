package com.sdas.server.pharmacy.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.pharmacy.model.DispenseStatus;
import com.sdas.server.pharmacy.service.DispenseStatusResolver;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 派生状态单测（T019）：data-model §4 的 8 行矩阵 + 边界。 */
class DispenseStatusResolverTest {

    private static final LocalDateTime VALID_UNTIL = LocalDateTime.of(2026, 11, 11, 10, 0, 0);

    private static final LocalDateTime BEFORE = VALID_UNTIL.minusSeconds(1);

    private static final LocalDateTime AT = VALID_UNTIL;

    private static final LocalDateTime AFTER = VALID_UNTIL.plusSeconds(1);

    @Test
    void row1SignedWithoutRecordAndNotExpiredIsPending() {
        assertDerived(PrescriptionStatus.SIGNED, false, BEFORE, DispenseStatus.PENDING, true);
    }

    @Test
    void row2SignedWithoutRecordAtDeadlineIsStillPending() {
        assertDerived(PrescriptionStatus.SIGNED, false, AT, DispenseStatus.PENDING, true);
    }

    @Test
    void row3SignedWithoutRecordAfterDeadlineIsExpired() {
        assertDerived(PrescriptionStatus.SIGNED, false, AFTER, DispenseStatus.EXPIRED, false);
    }

    @Test
    void row4SignedWithRecordBeforeDeadlineIsDispensed() {
        assertDerived(PrescriptionStatus.SIGNED, true, BEFORE, DispenseStatus.DISPENSED, false);
    }

    @Test
    void row5SignedWithRecordAfterDeadlineStaysDispensed() {
        assertDerived(PrescriptionStatus.SIGNED, true, AFTER, DispenseStatus.DISPENSED, false);
    }

    @Test
    void row6ExpiredStatusWithoutRecordIsExpired() {
        assertDerived(PrescriptionStatus.EXPIRED, false, AFTER, DispenseStatus.EXPIRED, false);
    }

    @Test
    void row7RevokedWithoutRecordIsRevoked() {
        assertDerived(PrescriptionStatus.REVOKED, false, BEFORE, DispenseStatus.REVOKED, false);
        assertDerived(PrescriptionStatus.REVOKED, false, AFTER, DispenseStatus.REVOKED, false);
    }

    @Test
    void row8RevokedWithRecordIsStillRevoked() {
        assertDerived(PrescriptionStatus.REVOKED, true, BEFORE, DispenseStatus.REVOKED, false);
    }

    @Test
    void nullValidUntilIsTreatedAsExpired() {
        assertEquals(DispenseStatus.EXPIRED,
                DispenseStatusResolver.resolve(PrescriptionStatus.SIGNED, false, null, BEFORE));
        assertTrue(DispenseStatusResolver.isExpired(null, BEFORE));
    }

    @Test
    void draftIsNotSpecialCasedInDerivation() {
        assertDerived(PrescriptionStatus.DRAFT, false, BEFORE, DispenseStatus.PENDING, true);
    }

    private void assertDerived(PrescriptionStatus status, boolean dispensed, LocalDateTime now,
                               DispenseStatus expected, boolean expectedDispensable) {
        DispenseStatus actual = DispenseStatusResolver.resolve(status, dispensed, VALID_UNTIL, now);
        assertEquals(expected, actual);
        assertEquals(expectedDispensable, DispenseStatusResolver.dispensable(actual));
        assertFalse(DispenseStatusResolver.isExpired(VALID_UNTIL, AT));
    }
}
