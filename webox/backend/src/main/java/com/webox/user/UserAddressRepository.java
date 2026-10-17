package com.webox.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findByUserIdOrderByLastUsedAtDesc(Long userId);

    Optional<UserAddress> findByUserIdAndAddressText(Long userId, String addressText);
}
