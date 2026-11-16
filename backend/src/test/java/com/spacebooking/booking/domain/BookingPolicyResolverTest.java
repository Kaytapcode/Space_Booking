package com.spacebooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** R08 (domain.md section 3). */
class BookingPolicyResolverTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"EMPLOYEE, EmployeePolicy", "MANAGER, ManagerPolicy"})
  void resolves(Role role, String policyClass) {
    assertThat(BookingPolicyResolver.forRole(role).getClass().getSimpleName()).isEqualTo(policyClass);
  }

  @Test
  void admin_accessDenied() {
    assertThatThrownBy(() -> BookingPolicyResolver.forRole(Role.ADMIN))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
  }
}
