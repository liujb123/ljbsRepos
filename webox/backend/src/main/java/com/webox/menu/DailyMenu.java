package com.webox.menu;

import com.webox.common.BaseAuditEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The set of dishes published for one business date (PRD 3.2 / 4.3). */
@Entity
@Table(name = "daily_menu")
@Getter
@Setter
@NoArgsConstructor
public class DailyMenu extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_date", nullable = false, unique = true)
    private LocalDate menuDate;

    @OneToMany(mappedBy = "dailyMenu", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    private List<DailyMenuItem> items = new ArrayList<>();

    public DailyMenu(LocalDate menuDate) {
        this.menuDate = menuDate;
    }

    public void addItem(DailyMenuItem item) {
        item.setDailyMenu(this);
        item.setDisplayOrder(items.size());
        this.items.add(item);
    }
}
