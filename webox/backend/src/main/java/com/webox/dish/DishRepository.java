package com.webox.dish;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DishRepository extends JpaRepository<Dish, Long> {

    /**
     * Admin listing: keyword (name), category and shelf status are all optional.
     *
     * <p>The keyword is bound as a parameter and escaped before it reaches here; there is
     * no string concatenation anywhere in this query.
     */
    @Query("""
            select d from Dish d
             where (:keyword is null or lower(d.name) like :keyword escape '\\')
               and (:category is null or d.category = :category)
               and (:available is null or d.available = :available)
            """)
    Page<Dish> searchForAdmin(@Param("keyword") String keyword,
                              @Param("category") DishCategory category,
                              @Param("available") Boolean available,
                              Pageable pageable);

    /** Count of dishes, used by the seeder to decide whether the catalog already exists. */
    @Override
    long count();

    List<Dish> findByAvailableTrueOrderByIdAsc();

    default List<Dish> findAllByIdIn(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : findAllById(ids);
    }

    Optional<Dish> findByName(String name);
}
