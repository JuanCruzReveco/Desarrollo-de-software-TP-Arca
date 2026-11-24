package ar.edu.utn.frm.facturacion.service.arca;

import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Traduce el agregado a los campos de WSFEv1 y rechaza datos que no podemos afirmar. */
public final class ArcaFiscalMapper {
    private ArcaFiscalMapper() {}

    public static int tipoPara(int emisor, int receptor) {
        if (emisor == 1) {
            if (receptor == 1 || receptor == 6) return 1;
            if (receptor == 4 || receptor == 5) return 6;
        } else if (emisor == 4 || emisor == 6) {
            if (receptor == 1 || receptor == 4 || receptor == 5 || receptor == 6) return 11;
        }
        throw new ReglaNegocioException("Combinación de condiciones IVA no admitida para facturación local");
    }

    public static ArcaComprobante desde(FacturaVenta f, int tipo) {
        if (tipo != 1 && tipo != 6 && tipo != 11) {
            throw new ReglaNegocioException("Solo se admiten facturas A, B o C");
        }
        if (f.getPuntoVenta() == null || f.getPuntoVenta().getNumero() <= 0) {
            throw new ReglaNegocioException("Falta un punto de venta de Web Services válido");
        }
        if (f.getFechaEmision() == null || f.getFechaEmision().isAfter(LocalDate.now())
                || f.getFechaEmision().isBefore(LocalDate.now().minusDays(5))) {
            throw new ReglaNegocioException("La fecha de emisión debe estar entre hoy y cinco días atrás");
        }
        if (f.getTipoMoneda() == null || !"PES".equals(f.getTipoMoneda().getCodigoAfip())) {
            throw new ReglaNegocioException("La cotización de otra moneda debe consultarse a ARCA antes de emitir");
        }
        if (f.getCondicionIva() == null || f.getCondicionIva().getCodigoAfip() <= 0) {
            throw new ReglaNegocioException("Falta la condición de IVA del receptor");
        }
        int docTipo;
        long docNro;
        if (f.getCliente() == null) {
            // RG 5700/2025 fija en $10.000.000 el umbral vigente para identificar
            // operaciones con consumidor final. Por encima del umbral no se inventa DNI/CUIT.
            BigDecimal totalSinIdentificar = dinero(f.getImporteTotal());
            BigDecimal umbralIdentificacion = new BigDecimal("10000000.00");
            if ((tipo != 6 && tipo != 11) || f.getCondicionIva().getCodigoAfip() != 5
                    || totalSinIdentificar.compareTo(umbralIdentificacion) >= 0) {
                throw new ReglaNegocioException("Consumidor Final sin identificar solo puede emitirse con factura B o C y total menor a $10.000.000; para importes mayores cargá un documento válido");
            }
            docTipo = 99;
            docNro = 0;
        } else {
            String cuit = f.getCliente().getCuitCuil() == null ? "" : f.getCliente().getCuitCuil().replaceAll("[^0-9]", "");
            if (!cuitValida(cuit)) throw new ReglaNegocioException("El CUIT/CUIL del cliente de ejemplo no es válido para ARCA. Para probar la demo elegí Consumidor Final sin cliente o cargá un documento real autorizado");
            docTipo = 80;
            docNro = Long.parseLong(cuit);
        }
        int receptor = f.getCondicionIva().getCodigoAfip();
        if (tipo == 1 && receptor != 1 && receptor != 6) {
            throw new ReglaNegocioException("La factura A requiere un receptor inscripto o monotributista identificado");
        }
        if (tipo == 6 && receptor != 5 && receptor != 4) {
            throw new ReglaNegocioException("La factura B requiere un receptor consumidor final o exento identificado");
        }
        if (f.getDetalles().isEmpty()) throw new ReglaNegocioException("La factura no tiene renglones");
        Map<Integer, BigDecimal[]> porAlicuota = new LinkedHashMap<>();
        BigDecimal neto = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        for (FacturaVentaDetalle d : f.getDetalles()) {
            if (d.getListaPrecioArticulo() == null || d.getListaPrecioArticulo().getArticulo() == null
                    || d.getListaPrecioArticulo().getArticulo().getAlicuotaIva() == null) {
                throw new ReglaNegocioException("Cada artículo debe tener una alícuota de IVA definida");
            }
            int codigo = tipo == 11 ? 0 : codigoAlicuota(d.getListaPrecioArticulo().getArticulo().getAlicuotaIva());
            BigDecimal base = dinero(d.getImporteNeto());
            BigDecimal impuesto = dinero(d.getImporteIva());
            if (tipo == 11 && impuesto.signum() != 0) throw new ReglaNegocioException("La factura C no discrimina IVA");
            if (base.signum() < 0 || impuesto.signum() < 0) throw new ReglaNegocioException("Los importes no pueden ser negativos");
            neto = neto.add(base);
            iva = iva.add(impuesto);
            if (tipo != 11) {
                BigDecimal[] suma = porAlicuota.computeIfAbsent(codigo, ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                suma[0] = suma[0].add(base);
                suma[1] = suma[1].add(impuesto);
            }
        }
        BigDecimal total = dinero(f.getImporteTotal());
        if (total.signum() <= 0 || neto.add(iva).compareTo(total) != 0) {
            throw new ReglaNegocioException("El total no coincide con neto más IVA");
        }
        List<ArcaComprobante.Alicuota> alicuotas = porAlicuota.entrySet().stream()
                .map(e -> new ArcaComprobante.Alicuota(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
        return new ArcaComprobante(f.getPuntoVenta().getNumero(), tipo, f.getFechaEmision(), docTipo,
                docNro, f.getCondicionIva().getCodigoAfip(), neto, iva, total, alicuotas);
    }

    private static BigDecimal dinero(double valor) {
        if (!Double.isFinite(valor)) throw new ReglaNegocioException("Importe no finito");
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static int codigoAlicuota(double porcentaje) {
        if (porcentaje == 0) return 3;
        if (porcentaje == 2.5) return 9;
        if (porcentaje == 5) return 8;
        if (porcentaje == 10.5) return 4;
        if (porcentaje == 21) return 5;
        if (porcentaje == 27) return 6;
        throw new ReglaNegocioException("Alícuota de IVA no admitida: " + porcentaje);
    }

    static boolean cuitValida(String cuit) {
        if (cuit == null || !cuit.matches("[0-9]{11}")) return false;
        if (cuit.chars().allMatch(ch -> ch == '0')) return false;
        int[] pesos = {5,4,3,2,7,6,5,4,3,2};
        int suma = 0;
        for (int i = 0; i < 10; i++) suma += Character.digit(cuit.charAt(i), 10) * pesos[i];
        int digito = 11 - suma % 11;
        if (digito == 11) digito = 0;
        else if (digito == 10) digito = 9;
        return digito == Character.digit(cuit.charAt(10), 10);
    }
}
