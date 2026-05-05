package model.persistence;

/**
 * Provee un ID persistente de máquina (UUID v4).
 * El mismo ID debe sobrevivir reinicios de la app.
 */
public interface MachineIdProvider {

    /**
     * Devuelve el UUID persistente de la máquina.
     * Si no existe o es inválido, genera y persiste uno nuevo.
     */
    String getMachineId();
}
