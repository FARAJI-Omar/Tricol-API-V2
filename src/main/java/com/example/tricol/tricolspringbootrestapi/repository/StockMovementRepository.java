package com.example.tricol.tricolspringbootrestapi.repository;

import com.example.tricol.tricolspringbootrestapi.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Integer>,
        JpaSpecificationExecutor<StockMovement> {
}
