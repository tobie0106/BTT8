package com.example.quanlysach2.controller;

import com.example.quanlysach2.entity.Book;
import com.example.quanlysach2.entity.Order;
import com.example.quanlysach2.entity.OrderDetail;
import com.example.quanlysach2.model.CartItem;
import com.example.quanlysach2.repository.BookRepository;
import com.example.quanlysach2.repository.OrderRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

@Controller
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private OrderRepository orderRepository;

    @GetMapping("/")
    public String home() {
        return "redirect:/books";
    }

    @GetMapping("/books")
    public String listBooks(Model model,
                            @RequestParam(defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "") String category,
                            @RequestParam(defaultValue = "priceAsc") String sort,
                            @RequestParam(defaultValue = "1") int page,
                            HttpSession session) {

        int pageSize = 5;
        Sort sorting = Sort.by("price");
        if ("priceDesc".equals(sort)) {
            sorting = sorting.descending();
        } else {
            sorting = sorting.ascending();
        }

        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), pageSize, sorting);
        Page<Book> pageBooks = bookRepository.findByTitleContainingIgnoreCaseAndCategoryContainingIgnoreCase(keyword, category, pageable);

        model.addAttribute("listBooks", pageBooks.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageBooks.getTotalPages());
        model.addAttribute("totalItems", pageBooks.getTotalElements());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("sort", sort);
        model.addAttribute("categories", bookRepository.findDistinctCategory());

        Map<Integer, CartItem> cart = getCart(session);
        int cartSize = cart.values().stream().mapToInt(CartItem::getQuantity).sum();
        model.addAttribute("cartSize", cartSize);

        return "books";
    }

    @GetMapping("/books/new")
    public String showCreateForm(Model model) {
        model.addAttribute("book", new Book());
        model.addAttribute("pageTitle", "Thêm sách mới");
        return "book-form";
    }

    @PostMapping("/books/save")
    public String saveBook(@Valid @ModelAttribute("book") Book book,
                           BindingResult result,
                           Model model) {
        if (result.hasErrors()) {
            model.addAttribute("pageTitle",
                    book.getId() == null ? "Thêm sách mới" : "Cập nhật sách");
            return "book-form";
        }

        bookRepository.save(book);
        return "redirect:/books";
    }

    @GetMapping("/books/edit/{id}")
    public String showEditForm(@PathVariable("id") Integer id, Model model) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sách có id: " + id));

        model.addAttribute("book", book);
        model.addAttribute("pageTitle", "Cập nhật sách");
        return "book-form";
    }

    @GetMapping("/books/delete/{id}")
    public String deleteBook(@PathVariable("id") Integer id) {
        if (!bookRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy sách có id: " + id);
        }

        bookRepository.deleteById(id);
        return "redirect:/books";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam("bookId") Integer bookId,
                            @RequestParam(defaultValue = "1") Integer quantity,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sách có id: " + bookId));

        if (quantity == null || quantity < 1) {
            quantity = 1;
        }

        Map<Integer, CartItem> cart = getCart(session);
        CartItem item = cart.get(bookId);
        int existingQuantity = item == null ? 0 : item.getQuantity();
        int requestedQuantity = existingQuantity + quantity;

        if (requestedQuantity > book.getQuantity()) {
            redirectAttributes.addFlashAttribute("error", "Số lượng yêu cầu lớn hơn tồn kho hiện có. Vui lòng nhập lại.");
            return "redirect:/books";
        }

        if (item == null) {
            item = new CartItem(book, quantity);
        } else {
            item.setQuantity(requestedQuantity);
        }
        cart.put(bookId, item);
        session.setAttribute("cart", cart);

        redirectAttributes.addFlashAttribute("message", "Đã thêm vào giỏ hàng: " + book.getTitle());
        return "redirect:/books";
    }

    @GetMapping("/cart")
    public String viewCart(Model model, HttpSession session) {
        List<CartItem> cartItems = new ArrayList<>(getCart(session).values());
        double cartTotal = cartItems.stream().mapToDouble(CartItem::getTotalPrice).sum();

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartTotal", cartTotal);
        return "cart";
    }

    @Transactional
    @PostMapping("/checkout")
    public String checkout(HttpSession session,
                           Principal principal,
                           RedirectAttributes redirectAttributes) {
        Map<Integer, CartItem> cart = getCart(session);
        if (cart.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Giỏ hàng đang trống.");
            return "redirect:/cart";
        }

        String validationError = validateCartStock(cart);
        if (validationError != null) {
            redirectAttributes.addFlashAttribute("error", validationError);
            return "redirect:/cart";
        }

        Order order = new Order();
        order.setUsername(principal.getName());
        order.setOrderDate(LocalDateTime.now());

        double total = cart.values().stream().mapToDouble(CartItem::getTotalPrice).sum();
        order.setTotal(total);

        for (CartItem item : cart.values()) {
            Book currentBook = bookRepository.findById(item.getBook().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sách có id: " + item.getBook().getId()));

            currentBook.setQuantity(currentBook.getQuantity() - item.getQuantity());
            bookRepository.save(currentBook);

            OrderDetail detail = new OrderDetail();
            detail.setBook(currentBook);
            detail.setQuantity(item.getQuantity());
            detail.setPrice(currentBook.getPrice());
            order.addDetail(detail);
        }

        orderRepository.save(order);
        session.removeAttribute("cart");

        redirectAttributes.addFlashAttribute("message", "Đặt hàng thành công. Tổng tiền: " + total + " VNĐ");
        return "redirect:/books";
    }

    private String validateCartStock(Map<Integer, CartItem> cart) {
        for (CartItem item : cart.values()) {
            Book currentBook = bookRepository.findById(item.getBook().getId())
                    .orElse(null);
            if (currentBook == null) {
                return "Sản phẩm trong giỏ không tồn tại nữa.";
            }
            int available = currentBook.getQuantity() == null ? 0 : currentBook.getQuantity();
            if (item.getQuantity() > available) {
                return "Số lượng trong giỏ lớn hơn tồn kho hiện có cho sách: " + currentBook.getTitle();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, CartItem> getCart(HttpSession session) {
        Object cartObject = session.getAttribute("cart");
        if (cartObject instanceof Map) {
            return (Map<Integer, CartItem>) cartObject;
        }

        Map<Integer, CartItem> cart = new LinkedHashMap<>();
        session.setAttribute("cart", cart);
        return cart;
    }
}
