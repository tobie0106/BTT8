package com.example.quanlysach2.repository;

import com.example.quanlysach2.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Integer> {

    Page<Book> findByTitleContainingIgnoreCaseAndCategoryContainingIgnoreCase(String title, String category, Pageable pageable);

    @Query("SELECT DISTINCT b.category FROM Book b")
    List<String> findDistinctCategory();
}
