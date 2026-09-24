package br.com.buscador.kabum;

public class KabumPayloadException extends RuntimeException {
    public KabumPayloadException(String message) {
        super(message);
    }

    public KabumPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
