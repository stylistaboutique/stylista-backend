package com.stylista.service;

import com.stylista.model.Tailor;
import com.stylista.repository.TailorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TailorService {

    private final TailorRepository tailorRepo;

    public TailorService(TailorRepository tailorRepo) {
        this.tailorRepo = tailorRepo;
    }

    public Tailor addTailor(Tailor tailor) {
        return tailorRepo.save(tailor);
    }

    public List<Tailor> activeTailors() {
        return tailorRepo.findByActiveTrueOrderByNameAsc();
    }

    public List<Tailor> allTailors() {
        return tailorRepo.findAll();
    }

    public Optional<Tailor> findById(Long id) {
        return tailorRepo.findById(id);
    }

    public Optional<Tailor> updateTailor(Long id, Tailor patch) {
        return tailorRepo.findById(id).map(t -> {
            if (patch.getName() != null)          t.setName(patch.getName());
            if (patch.getMobile() != null)        t.setMobile(patch.getMobile());
            if (patch.getBoutiqueName() != null)  t.setBoutiqueName(patch.getBoutiqueName());
            if (patch.getCity() != null)          t.setCity(patch.getCity());
            if (patch.getSpecialization() != null) t.setSpecialization(patch.getSpecialization());
            t.setActive(patch.isActive());
            return tailorRepo.save(t);
        });
    }

    public boolean deactivateTailor(Long id) {
        return tailorRepo.findById(id).map(t -> {
            t.setActive(false);
            tailorRepo.save(t);
            return true;
        }).orElse(false);
    }
}
