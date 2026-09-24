package br.com.buscador.robots;

public class DisallowedUrlException extends RuntimeException {
    public DisallowedUrlException(String message) {
        super(message);
    }
}
