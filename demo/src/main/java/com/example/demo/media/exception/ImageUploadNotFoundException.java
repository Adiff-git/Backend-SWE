package com.example.demo.media.exception;

import com.example.demo.common.exception.ApplicationException;

public class ImageUploadNotFoundException extends ApplicationException {
    public ImageUploadNotFoundException() { super(404, "Image not found"); }
}
