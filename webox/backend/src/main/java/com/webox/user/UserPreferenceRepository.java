package com.webox.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    // Preferences are addressed by user id (the primary key), so no derived queries are needed.
    @Override
    List<UserPreference> findAllById(Iterable<Long> ids);
}
