package com.saaketh.budget.category;

public class CategoryNameTakenException extends RuntimeException {

    public CategoryNameTakenException() {
        super("You already have a category with this name");
    }
}
