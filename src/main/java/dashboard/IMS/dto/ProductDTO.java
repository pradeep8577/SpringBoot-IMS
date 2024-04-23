package dashboard.IMS.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data Transfer Object (DTO) class for Product.
 * Represents product data to be transferred between layers of the application.
 *
 * @author Pradeep Pawar
 * @date 02/20/2024
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductDTO {
    private Integer id;
    private String productName;
    private String productDescription;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private String imageUrls;
    private Integer userId; // Add userId field to ProductDTO

}
