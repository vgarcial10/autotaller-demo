package com.autotaller.gui;

import com.autotaller.client.AutoTallerClient;
import com.autotaller.client.AutoTallerClient.RespuestaApi;
import com.autotaller.db.Orden;
import com.autotaller.db.OrdenesRepository;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Interfaz grafica de demostracion para el proyecto AutoTaller.
 * Reutiliza AutoTallerClient (el mismo cliente ya validado con JUnit)
 * para los webhooks, y OrdenesRepository (JDBC de solo lectura) para
 * la tabla resumida de ordenes.
 */
public class DemoApp extends JFrame {

    private static final Color COLOR_FONDO = new Color(245, 246, 250);
    private static final Color COLOR_PRIMARIO = new Color(30, 39, 97);
    private static final Color COLOR_EXITO = new Color(34, 139, 34);
    private static final Color COLOR_ERROR = new Color(178, 34, 34);
    private static final Color COLOR_PENDIENTE = new Color(190, 190, 190);
    private static final Color COLOR_ASIGNADO = new Color(30, 90, 190);
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AutoTallerClient cliente = new AutoTallerClient();
    private final OrdenesRepository repositorioOrdenes = new OrdenesRepository();

    // --- Columna 1: nueva solicitud ---
    private final JTextField campoNombre = new JTextField();
    private final JTextField campoTelefono = new JTextField();
    private final JTextField campoPlaca = new JTextField();
    private final JTextField campoMarca = new JTextField();
    private final JTextField campoModelo = new JTextField();
    private final JTextField campoProblema = new JTextField();
    private final JLabel resultadoSolicitud = new JLabel(" ");

    // --- Columna 2: consultar orden (presentacion con etiquetas, no JSON) ---
    private final JTextField campoCodigoConsulta = new JTextField();
    private final JLabel mensajeConsulta = new JLabel(" ");
    private final JLabel valorId = valorEtiqueta();
    private final JLabel valorCliente = valorEtiqueta();
    private final JLabel valorVehiculo = valorEtiqueta();
    private final JLabel valorProblema = valorEtiqueta();
    private final JLabel valorPrioridad = valorEtiqueta();
    private final JLabel valorEstado = crearInsignia();
    private final JLabel valorCodigo = valorEtiqueta();
    private final JLabel valorCreado = valorEtiqueta();

    // --- Columna 3: tabla resumida ---
    private final DefaultTableModel modeloTabla = new DefaultTableModel(
            new Object[]{"Orden", "Estado", "Codigo", "Creado"}, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tablaOrdenes = new JTable(modeloTabla);
    private final JComboBox<String> comboOrden = new JComboBox<>(new String[]{"Mas recientes", "Pendientes arriba"});
    private final JCheckBox checkAutoActualizar = new JCheckBox("Auto");
    private final JLabel estadoTabla = new JLabel(" ");
    private Timer timerTabla;

    // --- Seguimiento en vivo ---
    private final JLabel pasoRecibido = crearPaso("1. Recibido");
    private final JLabel pasoAsignado = crearPaso("2. Asignado");
    private final JLabel pasoNotificado = crearPaso("3. Notificado");
    private final JLabel codigoSeguimiento = new JLabel(" ");
    private final JLabel estadoSeguimiento = new JLabel(" ");
    private SeguimientoWorker seguimientoActivo;

    public DemoApp() {
        super("AutoTaller - Demostracion en vivo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(COLOR_FONDO);
        setLayout(new BorderLayout(10, 10));

        add(construirEncabezado(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new GridLayout(1, 3, 15, 0));
        centro.setBackground(COLOR_FONDO);
        centro.setBorder(new EmptyBorder(10, 15, 10, 15));
        centro.add(construirPanelSolicitud());
        centro.add(construirPanelConsulta());
        centro.add(construirPanelTablaResumen());
        add(centro, BorderLayout.CENTER);

        add(construirPanelSeguimiento(), BorderLayout.SOUTH);

        setSize(1280, 700);
        setLocationRelativeTo(null);

        SwingUtilities.invokeLater(this::actualizarTabla);
    }

    private JComponent construirEncabezado() {
        JLabel titulo = new JLabel("AutoTaller - Automatizacion con n8n + PostgreSQL", SwingConstants.CENTER);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 22));
        titulo.setForeground(COLOR_PRIMARIO);
        titulo.setBorder(new EmptyBorder(15, 10, 5, 10));
        return titulo;
    }

    private JPanel panelBase() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(15, 15, 15, 15)));
        return panel;
    }

    private JLabel tituloPanel(String texto) {
        JLabel titulo = new JLabel(texto);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 16));
        return titulo;
    }

    // ======================= COLUMNA 1: NUEVA SOLICITUD =======================

    private JPanel construirPanelSolicitud() {
        JPanel panel = panelBase();

        JPanel formulario = new JPanel(new GridLayout(6, 2, 6, 6));
        formulario.setBackground(Color.WHITE);
        formulario.add(new JLabel("Nombre del cliente:"));
        formulario.add(campoNombre);
        formulario.add(new JLabel("Telefono:"));
        formulario.add(campoTelefono);
        formulario.add(new JLabel("Placa del vehiculo:"));
        formulario.add(campoPlaca);
        formulario.add(new JLabel("Marca:"));
        formulario.add(campoMarca);
        formulario.add(new JLabel("Modelo:"));
        formulario.add(campoModelo);
        formulario.add(new JLabel("Problema reportado:"));
        formulario.add(campoProblema);

        precargarEjemplo();

        JButton botonEnviar = new JButton("Enviar solicitud");
        botonEnviar.setBackground(COLOR_PRIMARIO);
        botonEnviar.setForeground(Color.WHITE);
        botonEnviar.setFocusPainted(false);
        botonEnviar.addActionListener(this::alEnviarSolicitud);

        resultadoSolicitud.setFont(new Font("SansSerif", Font.PLAIN, 13));
        resultadoSolicitud.setVerticalAlignment(SwingConstants.TOP);
        resultadoSolicitud.setBorder(new EmptyBorder(10, 2, 0, 2));

        JPanel norte = new JPanel(new BorderLayout());
        norte.setBackground(Color.WHITE);
        norte.add(tituloPanel("Workflow 1 - Nueva solicitud"), BorderLayout.NORTH);
        norte.add(formulario, BorderLayout.CENTER);

        panel.add(norte, BorderLayout.NORTH);
        panel.add(botonEnviar, BorderLayout.CENTER);
        panel.add(resultadoSolicitud, BorderLayout.SOUTH);
        return panel;
    }

    private void precargarEjemplo() {
        campoNombre.setText("Cliente Demostracion");
        campoTelefono.setText("5555-0000");
        campoPlaca.setText("DEMO01");
        campoMarca.setText("Toyota");
        campoModelo.setText("Corolla");
        campoProblema.setText("El motor no enciende");
    }

    private void alEnviarSolicitud(ActionEvent evento) {
        resultadoSolicitud.setForeground(Color.DARK_GRAY);
        resultadoSolicitud.setText("<html>Enviando solicitud...</html>");

        new SwingWorker<RespuestaApi, Void>() {
            @Override
            protected RespuestaApi doInBackground() throws Exception {
                return cliente.enviarSolicitudServicio(
                        campoNombre.getText(), campoTelefono.getText(), campoPlaca.getText(),
                        campoMarca.getText(), campoModelo.getText(), campoProblema.getText());
            }

            @Override
            protected void done() {
                try {
                    RespuestaApi respuesta = get();
                    if (respuesta.codigoEstado == 200) {
                        String codigo = respuesta.cuerpo.getString("codigo_acceso");
                        resultadoSolicitud.setForeground(COLOR_EXITO);
                        resultadoSolicitud.setText("<html><b>200 OK</b><br>Orden #" +
                                respuesta.cuerpo.optInt("orden_id") + " creada.<br>Codigo: " + codigo + "</html>");
                        campoCodigoConsulta.setText(codigo);
                        iniciarSeguimiento(codigo);
                        actualizarTabla();
                    } else {
                        resultadoSolicitud.setForeground(COLOR_ERROR);
                        resultadoSolicitud.setText("<html><b>" + respuesta.codigoEstado + "</b><br>" +
                                respuesta.cuerpo.optString("error", "Solicitud rechazada") + "</html>");
                    }
                } catch (InterruptedException | ExecutionException e) {
                    resultadoSolicitud.setForeground(COLOR_ERROR);
                    resultadoSolicitud.setText("<html>Error de conexion: " + e.getCause() + "</html>");
                }
            }
        }.execute();
    }

    // ======================= COLUMNA 2: CONSULTAR ORDEN =======================

    private JLabel valorEtiqueta() {
        JLabel label = new JLabel("-");
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return label;
    }

    private JLabel crearInsignia() {
        JLabel label = new JLabel("-", SwingConstants.CENTER);
        label.setOpaque(true);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setBackground(COLOR_PENDIENTE);
        label.setBorder(new EmptyBorder(3, 10, 3, 10));
        return label;
    }

    private JPanel construirPanelConsulta() {
        JPanel panel = panelBase();

        JPanel fila = new JPanel(new BorderLayout(8, 0));
        fila.setBackground(Color.WHITE);
        fila.add(new JLabel("Codigo de acceso:"), BorderLayout.WEST);
        fila.add(campoCodigoConsulta, BorderLayout.CENTER);

        JButton botonConsultar = new JButton("Consultar");
        botonConsultar.setBackground(COLOR_PRIMARIO);
        botonConsultar.setForeground(Color.WHITE);
        botonConsultar.setFocusPainted(false);
        botonConsultar.addActionListener(this::alConsultarOrden);

        JPanel norte = new JPanel(new BorderLayout(0, 8));
        norte.setBackground(Color.WHITE);
        norte.add(tituloPanel("Workflow 4 - Consultar orden"), BorderLayout.NORTH);
        norte.add(fila, BorderLayout.CENTER);
        norte.add(botonConsultar, BorderLayout.SOUTH);

        JPanel ficha = new JPanel(new GridLayout(8, 2, 6, 10));
        ficha.setBackground(Color.WHITE);
        ficha.setBorder(new EmptyBorder(15, 0, 0, 0));
        ficha.add(etiquetaCampo("ID de orden:"));
        ficha.add(valorId);
        ficha.add(etiquetaCampo("Cliente (ID):"));
        ficha.add(valorCliente);
        ficha.add(etiquetaCampo("Vehiculo (ID):"));
        ficha.add(valorVehiculo);
        ficha.add(etiquetaCampo("Problema:"));
        ficha.add(valorProblema);
        ficha.add(etiquetaCampo("Prioridad:"));
        ficha.add(valorPrioridad);
        ficha.add(etiquetaCampo("Estado:"));
        ficha.add(valorEstado);
        ficha.add(etiquetaCampo("Codigo de acceso:"));
        ficha.add(valorCodigo);
        ficha.add(etiquetaCampo("Creado:"));
        ficha.add(valorCreado);

        mensajeConsulta.setFont(new Font("SansSerif", Font.BOLD, 13));

        JPanel centro = new JPanel(new BorderLayout());
        centro.setBackground(Color.WHITE);
        centro.add(mensajeConsulta, BorderLayout.NORTH);
        centro.add(ficha, BorderLayout.CENTER);

        panel.add(norte, BorderLayout.NORTH);
        panel.add(centro, BorderLayout.CENTER);
        return panel;
    }

    private JLabel etiquetaCampo(String texto) {
        JLabel label = new JLabel(texto);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(new Color(90, 90, 90));
        return label;
    }

    private void alConsultarOrden(ActionEvent evento) {
        String codigo = campoCodigoConsulta.getText().trim();
        mensajeConsulta.setForeground(Color.DARK_GRAY);
        mensajeConsulta.setText("Consultando...");

        new SwingWorker<RespuestaApi, Void>() {
            @Override
            protected RespuestaApi doInBackground() throws Exception {
                return cliente.consultarOrden(codigo);
            }

            @Override
            protected void done() {
                try {
                    RespuestaApi respuesta = get();
                    if (respuesta.codigoEstado == 200) {
                        mensajeConsulta.setText(" ");
                        valorId.setText("#" + respuesta.cuerpo.optInt("id"));
                        valorCliente.setText(respuesta.cuerpo.optString("cliente_id", "-"));
                        valorVehiculo.setText(respuesta.cuerpo.optString("vehiculo_id", "-"));
                        valorProblema.setText(respuesta.cuerpo.optString("problema", "-"));
                        valorPrioridad.setText(respuesta.cuerpo.optString("prioridad", "(sin asignar aun)"));
                        valorCodigo.setText(respuesta.cuerpo.optString("codigo_acceso", "-"));
                        valorCreado.setText(formatearFecha(respuesta.cuerpo.optString("creado_en", "-")));
                        aplicarColorEstado(valorEstado, respuesta.cuerpo.optString("estado", "-"));
                        valorEstado.setText(respuesta.cuerpo.optString("estado", "-"));
                    } else {
                        limpiarFicha();
                        mensajeConsulta.setForeground(COLOR_ERROR);
                        mensajeConsulta.setText(respuesta.codigoEstado + " - " +
                                respuesta.cuerpo.optString("error", "Acceso denegado"));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    limpiarFicha();
                    mensajeConsulta.setForeground(COLOR_ERROR);
                    mensajeConsulta.setText("Error de conexion: " + e.getCause());
                }
            }
        }.execute();
    }

    private void limpiarFicha() {
        valorId.setText("-");
        valorCliente.setText("-");
        valorVehiculo.setText("-");
        valorProblema.setText("-");
        valorPrioridad.setText("-");
        valorCodigo.setText("-");
        valorCreado.setText("-");
        valorEstado.setText("-");
        valorEstado.setBackground(COLOR_PENDIENTE);
        valorEstado.setForeground(Color.DARK_GRAY);
    }

    private String formatearFecha(String isoFecha) {
        if (isoFecha == null || isoFecha.equals("-")) {
            return "-";
        }
        String limpia = isoFecha.replace("T", " ");
        int puntoMilisegundos = limpia.indexOf('.');
        if (puntoMilisegundos > 0) {
            limpia = limpia.substring(0, puntoMilisegundos);
        }
        return limpia.replace("Z", "");
    }

    // ======================= COLUMNA 3: TABLA RESUMIDA =======================

    private JPanel construirPanelTablaResumen() {
        JPanel panel = panelBase();

        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controles.setBackground(Color.WHITE);
        comboOrden.setFont(new Font("SansSerif", Font.PLAIN, 12));
        comboOrden.addActionListener(e -> actualizarTabla());
        controles.add(comboOrden);

        JButton botonActualizar = new JButton("Actualizar");
        botonActualizar.setBackground(COLOR_PRIMARIO);
        botonActualizar.setForeground(Color.WHITE);
        botonActualizar.setFocusPainted(false);
        botonActualizar.addActionListener(e -> actualizarTabla());
        controles.add(botonActualizar);

        checkAutoActualizar.setBackground(Color.WHITE);
        checkAutoActualizar.addActionListener(e -> alternarAutoActualizar());
        controles.add(checkAutoActualizar);

        tablaOrdenes.setRowHeight(26);
        tablaOrdenes.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tablaOrdenes.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        tablaOrdenes.getColumnModel().getColumn(1).setCellRenderer(new RenderizadorEstado());
        tablaOrdenes.setFillsViewportHeight(true);

        JPanel norte = new JPanel(new BorderLayout());
        norte.setBackground(Color.WHITE);
        norte.add(tituloPanel("Ordenes (en vivo desde PostgreSQL)"), BorderLayout.NORTH);
        norte.add(controles, BorderLayout.CENTER);

        panel.add(norte, BorderLayout.NORTH);
        panel.add(new JScrollPane(tablaOrdenes), BorderLayout.CENTER);
        panel.add(estadoTabla, BorderLayout.SOUTH);
        return panel;
    }

    private void alternarAutoActualizar() {
        if (checkAutoActualizar.isSelected()) {
            if (timerTabla == null) {
                timerTabla = new Timer(4000, e -> actualizarTabla());
            }
            timerTabla.start();
        } else if (timerTabla != null) {
            timerTabla.stop();
        }
    }

    private void actualizarTabla() {
        String criterio = comboOrden.getSelectedIndex() == 1
                ? OrdenesRepository.ORDEN_PENDIENTES
                : OrdenesRepository.ORDEN_RECIENTES;

        new SwingWorker<List<Orden>, Void>() {
            @Override
            protected List<Orden> doInBackground() throws Exception {
                return repositorioOrdenes.listarOrdenes(criterio);
            }

            @Override
            protected void done() {
                try {
                    List<Orden> ordenes = get();
                    modeloTabla.setRowCount(0);
                    for (Orden o : ordenes) {
                        modeloTabla.addRow(new Object[]{
                                "#" + o.id(),
                                o.estado(),
                                o.codigoAcceso(),
                                formatearFecha(o.creadoEn().toString()),
                        });
                    }
                    estadoTabla.setForeground(Color.DARK_GRAY);
                    estadoTabla.setText(ordenes.size() + " orden(es) - actualizado " +
                            java.time.LocalTime.now().format(FORMATO_HORA));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    estadoTabla.setForeground(COLOR_ERROR);
                    Throwable causa = e.getCause();
                    String detalle = (causa instanceof SQLException) ? causa.getMessage() : String.valueOf(causa);
                    estadoTabla.setText("No se pudo leer la base de datos: " + detalle);
                }
            }
        }.execute();
    }

    private static void aplicarColorEstado(JLabel label, String estado) {
        switch (estado) {
            case "recibido" -> {
                label.setBackground(COLOR_PENDIENTE);
                label.setForeground(Color.DARK_GRAY);
            }
            case "asignado" -> {
                label.setBackground(COLOR_ASIGNADO);
                label.setForeground(Color.WHITE);
            }
            case "notificado" -> {
                label.setBackground(COLOR_EXITO);
                label.setForeground(Color.WHITE);
            }
            default -> {
                label.setBackground(Color.LIGHT_GRAY);
                label.setForeground(Color.DARK_GRAY);
            }
        }
    }

    /** Colorea la columna Estado de la tabla igual que la insignia de la ficha de consulta. */
    private static class RenderizadorEstado extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionado,
                                                         boolean tieneFoco, int fila, int columna) {
            Component c = super.getTableCellRendererComponent(tabla, valor, seleccionado, tieneFoco, fila, columna);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!seleccionado) {
                aplicarColorEstado(this, String.valueOf(valor));
            }
            return c;
        }
    }

    // ======================= SEGUIMIENTO EN VIVO =======================

    private JPanel construirPanelSeguimiento() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(12, 15, 15, 15)));

        JLabel titulo = new JLabel("Seguimiento en vivo (Workflows 2 y 3, automaticos cada 1 minuto)");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 15));
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel pasos = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 5));
        pasos.setBackground(Color.WHITE);
        pasos.add(pasoRecibido);
        pasos.add(flecha());
        pasos.add(pasoAsignado);
        pasos.add(flecha());
        pasos.add(pasoNotificado);
        pasos.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel infoFila = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        infoFila.setBackground(Color.WHITE);
        infoFila.add(new JLabel("Orden en seguimiento:"));
        infoFila.add(codigoSeguimiento);
        infoFila.add(estadoSeguimiento);
        infoFila.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(titulo);
        panel.add(Box.createVerticalStrut(8));
        panel.add(pasos);
        panel.add(infoFila);

        marcarPasoPendiente(pasoRecibido);
        marcarPasoPendiente(pasoAsignado);
        marcarPasoPendiente(pasoNotificado);

        return panel;
    }

    private JLabel flecha() {
        JLabel l = new JLabel("→");
        l.setFont(new Font("SansSerif", Font.BOLD, 20));
        l.setForeground(new Color(180, 180, 180));
        return l;
    }

    private JLabel crearPaso(String texto) {
        JLabel label = new JLabel(texto, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setPreferredSize(new Dimension(130, 36));
        return label;
    }

    private void marcarPasoPendiente(JLabel label) {
        label.setBackground(COLOR_PENDIENTE);
        label.setForeground(Color.DARK_GRAY);
    }

    private void marcarPasoActivo(JLabel label) {
        label.setBackground(COLOR_PRIMARIO);
        label.setForeground(Color.WHITE);
    }

    private void marcarPasoCompleto(JLabel label) {
        label.setBackground(COLOR_EXITO);
        label.setForeground(Color.WHITE);
    }

    private void iniciarSeguimiento(String codigoAcceso) {
        if (seguimientoActivo != null && !seguimientoActivo.isDone()) {
            seguimientoActivo.cancel(true);
        }
        marcarPasoPendiente(pasoAsignado);
        marcarPasoPendiente(pasoNotificado);
        marcarPasoActivo(pasoRecibido);
        codigoSeguimiento.setText(codigoAcceso);
        estadoSeguimiento.setText("(esperando al Schedule Trigger de n8n...)");

        seguimientoActivo = new SeguimientoWorker(codigoAcceso);
        seguimientoActivo.execute();
    }

    /** Sondea el Workflow 4 cada 4 segundos y publica cambios de estado en la UI, sin congelarla. */
    private class SeguimientoWorker extends SwingWorker<Void, String> {
        private final String codigoAcceso;

        SeguimientoWorker(String codigoAcceso) {
            this.codigoAcceso = codigoAcceso;
        }

        @Override
        protected Void doInBackground() throws Exception {
            String ultimoEstado = null;
            long inicio = System.currentTimeMillis();
            while (!isCancelled() && System.currentTimeMillis() - inicio < 300_000) {
                RespuestaApi respuesta = cliente.consultarOrden(codigoAcceso);
                if (respuesta.codigoEstado == 200) {
                    String estado = respuesta.cuerpo.optString("estado", null);
                    if (estado != null && !estado.equals(ultimoEstado)) {
                        ultimoEstado = estado;
                        publish(estado);
                        if ("notificado".equals(estado)) {
                            return null;
                        }
                    }
                }
                Thread.sleep(4000);
            }
            return null;
        }

        @Override
        protected void process(List<String> estados) {
            String estado = estados.get(estados.size() - 1);
            estadoSeguimiento.setText("Estado actual: " + estado);
            switch (estado) {
                case "recibido" -> marcarPasoActivo(pasoRecibido);
                case "asignado" -> {
                    marcarPasoCompleto(pasoRecibido);
                    marcarPasoActivo(pasoAsignado);
                }
                case "notificado" -> {
                    marcarPasoCompleto(pasoRecibido);
                    marcarPasoCompleto(pasoAsignado);
                    marcarPasoCompleto(pasoNotificado);
                    estadoSeguimiento.setText("Ciclo completo. Estado final: notificado");
                }
                default -> {
                }
            }
            actualizarTabla();
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Se queda con el look and feel por defecto si falla
        }
        SwingUtilities.invokeLater(() -> new DemoApp().setVisible(true));
    }
}
