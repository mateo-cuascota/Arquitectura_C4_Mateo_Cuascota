package ec.edu.c4.ant.client;

import ec.edu.c4.ant.model.LicenciaInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte la respuesta de clp_grid_citaciones.jsp en {@link LicenciaInfo}.
 * La ANT responde una grilla (XML de jqGrid &lt;rows&gt;&lt;row&gt;&lt;cell&gt; o tabla HTML),
 * por eso se soportan ambos formatos.
 */
@Component
public class AntResponseParser {

    private static final Pattern PUNTOS_TEXTO = Pattern.compile(
            "puntos?\\s*(?:de\\s+la\\s+licencia|disponibles|actuales)?\\s*[:=]?\\s*(\\d{1,2}(?:[.,]\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    public LicenciaInfo parse(String body, String cedula, String placa) {
        String trimmed = body.trim();
        List<Map<String, String>> filas = trimmed.startsWith("<?xml") || trimmed.startsWith("<rows")
                ? parseJqGridXml(trimmed)
                : parseHtmlTable(trimmed);

        Double descontados = sumarColumnaPuntos(filas);
        Integer disponibles = buscarPuntosEnTexto(trimmed);
        if (disponibles == null && descontados != null) {
            disponibles = (int) Math.max(0, Math.round(LicenciaInfo.PUNTOS_MAXIMOS - descontados));
        }
        if (disponibles == null && filas.isEmpty()) {
            // Sin citaciones registradas: la licencia conserva todos sus puntos.
            disponibles = LicenciaInfo.PUNTOS_MAXIMOS;
            descontados = 0.0;
        }
        return new LicenciaInfo(cedula, placa, disponibles, descontados, filas.size(), filas);
    }

    private List<Map<String, String>> parseJqGridXml(String xml) {
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        List<Map<String, String>> filas = new ArrayList<>();
        for (Element row : doc.select("row")) {
            Map<String, String> fila = new LinkedHashMap<>();
            Elements cells = row.select("cell");
            for (int i = 0; i < cells.size(); i++) {
                fila.put("col" + (i + 1), Jsoup.parse(cells.get(i).text()).text());
            }
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, String>> parseHtmlTable(String html) {
        Document doc = Jsoup.parse(html);
        List<Map<String, String>> filas = new ArrayList<>();
        for (Element table : doc.select("table")) {
            Elements rows = table.select("tr");
            if (rows.size() < 2) {
                continue;
            }
            List<String> headers = rows.get(0).select("th,td").eachText();
            for (int r = 1; r < rows.size(); r++) {
                Elements cells = rows.get(r).select("td");
                if (cells.isEmpty()) {
                    continue;
                }
                Map<String, String> fila = new LinkedHashMap<>();
                for (int i = 0; i < cells.size(); i++) {
                    String header = i < headers.size() && !headers.get(i).isBlank() ? headers.get(i) : "col" + (i + 1);
                    fila.put(header, cells.get(i).text());
                }
                filas.add(fila);
            }
        }
        return filas;
    }

    private Double sumarColumnaPuntos(List<Map<String, String>> filas) {
        double total = 0;
        boolean encontrada = false;
        for (Map<String, String> fila : filas) {
            for (Map.Entry<String, String> e : fila.entrySet()) {
                if (e.getKey().toLowerCase(Locale.ROOT).contains("punto")) {
                    try {
                        total += Double.parseDouble(e.getValue().replace(',', '.').trim());
                        encontrada = true;
                    } catch (NumberFormatException ignored) {
                        // celda sin numero
                    }
                }
            }
        }
        return encontrada ? total : null;
    }

    private Integer buscarPuntosEnTexto(String body) {
        Matcher m = PUNTOS_TEXTO.matcher(Jsoup.parse(body).text());
        if (m.find()) {
            double v = Double.parseDouble(m.group(1).replace(',', '.'));
            if (v >= 0 && v <= LicenciaInfo.PUNTOS_MAXIMOS) {
                return (int) Math.round(v);
            }
        }
        return null;
    }
}
