package com.pdf.printer.todo;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    public List<Todo> getAllTodos() {
        return todoRepository.findAll();
    }

    public Optional<Todo> getTodoById(Long id) {
        return todoRepository.findById(id);
    }

    public Todo createTodo(Todo todo) {
        return todoRepository.save(todo);
    }

    public Todo updateTodo(Long id, Todo updatedTodo) {
        return todoRepository.findById(id)
                .map(todo -> {
                    todo.setName(updatedTodo.getName());
                    todo.setPhoneNumber(updatedTodo.getPhoneNumber());
                    todo.setMessage(updatedTodo.getMessage());
                    todo.setCurrentDate(updatedTodo.getCurrentDate());
                    todo.setCompleted(updatedTodo.isCompleted());
                    return todoRepository.save(todo);
                })
                .orElse(null);
    }

    public void deleteTodo(Long id) {
        todoRepository.deleteById(id);
    }
}
