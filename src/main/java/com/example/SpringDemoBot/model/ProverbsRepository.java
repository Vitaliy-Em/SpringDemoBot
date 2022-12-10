package com.example.SpringDemoBot.model;

import org.springframework.data.repository.CrudRepository;

public interface ProverbsRepository extends CrudRepository<Proverb, Long> {
}
