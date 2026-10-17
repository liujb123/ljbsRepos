package com.webox.menu;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyMenuRepository extends JpaRepository<DailyMenu, Long> {

    Optional<DailyMenu> findByMenuDate(LocalDate menuDate);

    boolean existsByMenuDate(LocalDate menuDate);

    /** Dates that actually have a published menu, used by the date pickers. */
    @Query("""
            select m.menuDate from DailyMenu m
             where m.menuDate between :from and :to
             order by m.menuDate asc
            """)
    List<LocalDate> findMenuDatesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
