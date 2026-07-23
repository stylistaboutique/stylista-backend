package com.stylista.repository;

import com.stylista.model.Tailor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TailorRepository extends JpaRepository<Tailor, Long> {
    List<Tailor> findByActiveTrueOrderByNameAsc();
}
