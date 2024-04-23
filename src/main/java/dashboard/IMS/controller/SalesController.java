package dashboard.IMS.controller;

import com.itextpdf.text.DocumentException;
import dashboard.IMS.dto.UserDTO;
import dashboard.IMS.entity.Product;
import dashboard.IMS.entity.ProductVariation;
import dashboard.IMS.entity.Sales;
import dashboard.IMS.entity.User;
import dashboard.IMS.repository.ProductVariationRepository;
import dashboard.IMS.repository.SalesRepository;
import dashboard.IMS.repository.UserRepository;
import dashboard.IMS.service.SalesService;
import dashboard.IMS.service.UserService;
import dashboard.IMS.utilities.ExcelUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.Optional;
import dashboard.IMS.utilities.PdfUtil;

/**
 * Controller class for handling sales-related requests.
 *
 * @author Pradeep Pawar
 * @date 02/20/2024
 */
@Controller
public class SalesController {
    @Autowired
    private ProductVariationRepository productVariationRepository;

    @Autowired
    private SalesRepository salesRepository; // Autowire SalesRepository

    @Autowired
    private UserService userService; // Autowire UserService

    @Autowired
    private UserRepository userRepository; // Autowire UserRepository

    @Autowired
    private SalesService salesService; // Autowire SalesService



    /**
     * Directs users to the sales report page.
     * Fetches sales data associated with the logged-in user and prepares it for display.
     *
     * @return Name of the sales report page.
     */
    @GetMapping("/sales-report")
    @ResponseStatus(HttpStatus.OK)
    public String salesReport(Model model, HttpServletRequest request, Pageable pageable, @RequestParam(defaultValue = "0") int page) {
        // Retrieve the logged-in user from the session
        UserDTO loggedInUser = (UserDTO) request.getSession().getAttribute("loggedInUser");

        // Check if the logged-in user is valid
        if (loggedInUser != null) {
            // Fetch the list of sales associated with the logged-in user from the repository
            pageable = PageRequest.of(page, 5); // 5 items per page

            Page<Sales> salesPage = salesService.getSales(loggedInUser, pageable);


            model.addAttribute("salesPage", salesPage);


            List<Sales> salesList = salesPage.getContent(); // Get sales list from page

            // Calculate the starting ID for this page
            int startingId = page * 5; // Change here

            model.addAttribute("startingId", startingId);

            model.addAttribute("profilePicture", loggedInUser.getProfilePicture());
            model.addAttribute("loggedInUser", loggedInUser);

            // Create a map to store product image URLs
            Map<Integer, String> productImageUrls = new HashMap<>();

            // For each sale, fetch the corresponding product details
            for (Sales sale : salesList) {
                // Retrieve the product variation using its ID
                ProductVariation productVariation = productVariationRepository.findById(sale.getProductVariationId()).orElse(null);

                if (productVariation != null) {
                    // Access the product object associated with the product variation
                    Product product = productVariation.getProduct();

                    if (product != null) {
                        // Set the product name in the sales object
                        sale.setProductName(product.getProductName()); // Add this line
                        // Retrieve the image URLs for the product
                        String imageUrls = product.getImageUrls();
                        if (imageUrls != null && !imageUrls.isEmpty()) {
                            // Remove square brackets if present
                            imageUrls = imageUrls.replaceAll("\\[|\\]", "");
                            String[] imageUrlArray = imageUrls.split(","); // Split by comma
                            if (imageUrlArray.length > 0) {
                                String firstImageUrl = imageUrlArray[0].trim(); // Get the first URL
                                // Remove leading backslash if present
                                if (firstImageUrl.startsWith("/")) {
                                    firstImageUrl = firstImageUrl.substring(1);
                                }
                                // Store the image URL in the map
                                productImageUrls.put(product.getId(), firstImageUrl);
                                // Set the image URL in the sales object
                                sale.setProductImageUrl(firstImageUrl);
                            }
                        }
                    }
                }
            }

            // Add the sales list and product image URLs to the model
            model.addAttribute("salesList", salesList);
            model.addAttribute("productImageUrls", productImageUrls);

            // Return the view name
            return "salesreport";
        } else {
            // If the user is not logged in, redirect to the login page
            return "redirect:/login";
        }
    }

    /**
     * Handles the sale of a product variation.
     * Validates the sale and updates the database with the sale details.
     *
     * @param productVariationId    The ID of the product variation to be sold.
     * @param quantity              The quantity to be sold.
     * @param redirectAttributes    Redirect attributes for passing messages.
     * @param request               HTTP servlet request.
     * @return Redirect to the sales report page after successful sale or to the products page with an error message.
     */
    @PostMapping("/sell-product-variation")
    @ResponseStatus(HttpStatus.OK)
    public String sellProductVariation(@RequestParam("productVariationId") Integer productVariationId,
                                       @RequestParam("quantity") Integer quantity,
                                       RedirectAttributes redirectAttributes,
                                       HttpServletRequest request) {

        // Retrieve the logged-in user from the session attribute
        UserDTO loggedInUserDTO = (UserDTO) request.getSession().getAttribute("loggedInUser");

        // Check if the logged-in user is valid
        if (loggedInUserDTO != null) {

            User loggedInUser = userRepository.findUserById(loggedInUserDTO.getId());
            // Retrieve the product variation using its ID
            Optional<ProductVariation> optionalProductVariation = productVariationRepository.findByIdAndProductUserId(productVariationId, loggedInUser.getId());

            if (quantity == null) {
                // If quantity is null, set messageType to "danger" and redirect
                redirectAttributes.addFlashAttribute("message", "Quantity is required for the sale.");
                redirectAttributes.addFlashAttribute("messageType", "danger");
                return "redirect:/products";
            } else if (quantity <= 0) {
                // If quantity is zero or negative, set messageType to "danger" and redirect
                redirectAttributes.addFlashAttribute("message", "Invalid quantity for the sale.");
                redirectAttributes.addFlashAttribute("messageType", "danger");
                return "redirect:/products";
            }
            if (optionalProductVariation.isPresent()) {
                ProductVariation productVariation = optionalProductVariation.get();

                // Check if the available quantity is sufficient
                if (productVariation.getQuantity() < quantity) {
                    // If quantity is insufficient, set messageType to "danger" and redirect
                    redirectAttributes.addFlashAttribute("message", "Quantity is insufficient for the sale.");
                    redirectAttributes.addFlashAttribute("messageType", "danger");
                    return "redirect:/products";
                }

                // Subtract the sold quantity from the current quantity of the product variation
                int remainingQuantity = productVariation.getQuantity() - quantity;
                if (remainingQuantity <= 0) {
                    // If remaining quantity is zero or negative, set messageType to "danger" and redirect
                    redirectAttributes.addFlashAttribute("message", "Invalid quantity for the sale.");
                    redirectAttributes.addFlashAttribute("messageType", "danger");
                    return "redirect:/products";
                }

                // Update the quantity with the remaining quantity
                productVariation.setQuantity(remainingQuantity);

                // Save the updated product variation to the database
                productVariationRepository.save(productVariation);

                // Access the product object associated with the product variation
                Product product = productVariation.getProduct();

                if (product != null) {
                    // Calculate total revenue, total cost, and total profit
                    BigDecimal totalRevenue = product.getSellingPrice().multiply(BigDecimal.valueOf(quantity));
                    BigDecimal totalCost = product.getCostPrice().multiply(BigDecimal.valueOf(quantity));
                    BigDecimal totalProfit = totalRevenue.subtract(totalCost);

                    // Create a new Sales object
                    Sales sales = Sales.builder()
                            .productVariationId(productVariationId)
                            .quantitySold(quantity)
                            .quantityRefunded(0) // Set quantityRefunded to 0 for a new sale
                            .totalRevenue(totalRevenue)
                            .totalCost(totalCost)
                            .totalProfit(totalProfit)
                            .transactionDate(LocalDateTime.now()) // Set the transaction date to current date and time
                            .user(loggedInUser) // Associate the current user with the sale record
                            .build();

                    // Save the sales record to the database
                    salesRepository.save(sales);

                    // Redirect to the sales report page
                    redirectAttributes.addFlashAttribute("message", "Sale successful.");
                    redirectAttributes.addFlashAttribute("messageType", "success");
                    return "redirect:/sales-report";
                }
            }
        }

        // If product or product variation not found, or user not logged in, set messageType to "danger" and redirect with error message
        redirectAttributes.addFlashAttribute("message", "Failed to sell product variation. Please try again.");
        redirectAttributes.addFlashAttribute("messageType", "danger");
        return "redirect:/products";
    }

    /**
     * Handles the refund of a product variation.
     * Validates the refund and updates the database with the refund details.
     *
     * @param productVariationId    The ID of the product variation to be refunded.
     * @param quantity              The quantity to be refunded.
     * @param redirectAttributes    Redirect attributes for passing messages.
     * @param request               HTTP servlet request.
     * @return Redirect to the sales report page after successful refund or to the products page with an error message.
     */
    @PostMapping("/refund-product-variation")
    @ResponseStatus(HttpStatus.OK)
    public String refundProductVariation(@RequestParam("productVariationId") Integer productVariationId,
                                         @RequestParam("quantity") Integer quantity,
                                         RedirectAttributes redirectAttributes,
                                         HttpServletRequest request) {
        // Retrieve the logged-in user from the session attribute
        UserDTO loggedInUserDTO = (UserDTO) request.getSession().getAttribute("loggedInUser");

        // Check if the logged-in user is valid
        if (loggedInUserDTO != null) {
            // Retrieve the User entity corresponding to the logged-in user
            User loggedInUser = userRepository.findUserById(loggedInUserDTO.getId());
            if (loggedInUser == null) {
                // Handle the case where the User entity is not found
                redirectAttributes.addFlashAttribute("message", "Failed to refund product variation. Please try again.");
                redirectAttributes.addFlashAttribute("messageType", "failure");
                return "redirect:/products";
            }

            // Retrieve the product variation
            ProductVariation productVariation = productVariationRepository.findById(productVariationId).orElse(null);
            if (productVariation == null) {
                // Handle the case where the ProductVariation entity is not found
                redirectAttributes.addFlashAttribute("message", "Failed to find product variation. Please try again.");
                redirectAttributes.addFlashAttribute("messageType", "failure");
                return "redirect:/products";
            }

            // Retrieve the sales records for the product variation
            List<Sales> salesRecords = salesRepository.findByProductVariationIdAndUserIdOrderByTransactionDateDesc(productVariationId, loggedInUser.getId());

            int totalSoldQuantity = salesRecords.stream()
                    .filter(record -> record.getQuantitySold() != null)
                    .mapToInt(Sales::getQuantitySold)
                    .sum();

            int totalRefundedQuantity = salesRecords.stream()
                    .filter(record -> record.getQuantityRefunded() != null)
                    .mapToInt(Sales::getQuantityRefunded)
                    .sum();

            if (totalSoldQuantity - totalRefundedQuantity - quantity < 0) {
                redirectAttributes.addFlashAttribute("message", "Refund quantity cannot be greater than total sold quantity.");
                redirectAttributes.addFlashAttribute("messageType", "failure");
                return "redirect:/products";
            }

            int remainingQuantityToRefund = quantity;
            for (Sales salesRecord : salesRecords) {
                if (salesRecord.getQuantitySold() != null && salesRecord.getQuantityRefunded() != null && remainingQuantityToRefund > 0) {
                    int quantitySold = salesRecord.getQuantitySold();
                    int quantityRefunded = salesRecord.getQuantityRefunded();
                    int quantityAvailableForRefund = quantitySold - quantityRefunded;

                    int quantityToRefund = Math.min(quantityAvailableForRefund, remainingQuantityToRefund);

                    if (quantityToRefund > 0) {
                        // Update the sales record for the refund
                        salesRecord.setQuantityRefunded(quantityRefunded + quantityToRefund);
                        salesRepository.save(salesRecord);

                        // Calculate refund
                        BigDecimal refundRevenue = salesRecord.getTotalRevenue().divide(BigDecimal.valueOf(quantitySold), RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(quantityToRefund));
                        BigDecimal refundCost = salesRecord.getTotalCost().divide(BigDecimal.valueOf(quantitySold), RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(quantityToRefund));
                        BigDecimal refundProfit = salesRecord.getTotalProfit().divide(BigDecimal.valueOf(quantitySold), RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(quantityToRefund));

                        // Create refund sales record
                        Sales refundSales = Sales.builder()
                                .productVariationId(productVariationId)
                                .quantitySold(quantityToRefund * -1) // Negative quantity for a refund
                                .totalRevenue(refundRevenue.negate()) // Negative revenue for a refund
                                .totalCost(refundCost.negate()) // Negative cost for a refund
                                .totalProfit(refundProfit.negate()) // Negative profit for a refund
                                .transactionDate(LocalDateTime.now()) // Set the transaction date to current date and time
                                .user(loggedInUser) // Associate the current user with the sale record
                                .isRefund(true) // Indicate that this is a refund
                                .build();

                        // Save the refund sales record to the database
                        salesRepository.save(refundSales);

                        // Update the product variation's quantity
                        productVariation.setQuantity(productVariation.getQuantity() + quantityToRefund);
                        productVariationRepository.save(productVariation);

                        // Update the remaining quantity to refund
                        remainingQuantityToRefund -= quantityToRefund;

                        System.out.println("Quantity sold: " + quantitySold);
                        System.out.println("Quantity refunded: " + quantityRefunded);
                        System.out.println("Quantity available for refund: " + quantityAvailableForRefund);
                        System.out.println("Quantity to refund: " + quantityToRefund);

                        System.out.println("Remaining quantity to refund: " + remainingQuantityToRefund);
                    }
                }
            }

            // Redirect to the sales report page
            redirectAttributes.addFlashAttribute("message", "Refund successful.");
            redirectAttributes.addFlashAttribute("messageType", "success");
            return "redirect:/sales-report";
        } else {
            // If user not logged in, redirect with error message
            redirectAttributes.addFlashAttribute("message", "Failed to refund product variation. Please try again.");
            redirectAttributes.addFlashAttribute("messageType", "failure");
            return "redirect:/products";
        }
    }


    /**
     * Generates a PDF report of all sales associated with the logged-in user.
     * The PDF is sent as a response to the client.
     *
     * @param request  The HTTP servlet request.
     * @param response The HTTP servlet response.
     * @throws DocumentException If there is an error while generating the PDF.
     * @throws IOException       If there is an error while writing the PDF to the response output stream.
     */
    @GetMapping("/sales-report/pdf")
    @ResponseStatus(HttpStatus.OK)
    public void salesReportPdf(HttpServletRequest request, HttpServletResponse response) throws DocumentException, IOException {
        // Retrieve the logged-in user from the session
        UserDTO loggedInUser = (UserDTO) request.getSession().getAttribute("loggedInUser");

        // Check if the logged-in user is valid
        if (loggedInUser != null) {
            // Fetch all the sales associated with the logged-in user from the repository
            List<Sales> salesList = salesRepository.findAllByUserId(loggedInUser.getId());

            // Set the product name for each sales record
            for (Sales sale : salesList) {
                ProductVariation productVariation = productVariationRepository.findById(sale.getProductVariationId()).orElse(null);
                if (productVariation != null) {
                    Product product = productVariation.getProduct();
                    if (product != null) {
                        sale.setProductName(product.getProductName());
                    }
                }
            }

            // Generate PDF
            byte[] pdfBytes = PdfUtil.generateSalesReportPdf(salesList);

            // Get current date and format it as a string
            String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Set response headers
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"sales-report-" + currentDate + ".pdf\"");

            // Write PDF bytes to response output stream
            response.getOutputStream().write(pdfBytes);
            response.getOutputStream().flush();
        }
    }


    /**
     * Generates an Excel report of all sales associated with the logged-in user.
     * The Excel file is sent as a response to the client.
     *
     * @param request  The HTTP servlet request.
     * @param response The HTTP servlet response.
     * @throws IOException If there is an error while writing the Excel file to the response output stream.
     */
    @GetMapping("/sales-report/excel")
    @ResponseStatus(HttpStatus.OK)
    public void salesReportExcel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Retrieve the logged-in user from the session
        UserDTO loggedInUser = (UserDTO) request.getSession().getAttribute("loggedInUser");

        // Check if the logged-in user is valid
        if (loggedInUser != null) {
            // Fetch all the sales associated with the logged-in user from the repository
            List<Sales> salesList = salesRepository.findAllByUserId(loggedInUser.getId());

            // Set the product name for each sales record
            for (Sales sale : salesList) {
                ProductVariation productVariation = productVariationRepository.findById(sale.getProductVariationId()).orElse(null);
                if (productVariation != null) {
                    Product product = productVariation.getProduct();
                    if (product != null) {
                        sale.setProductName(product.getProductName());
                    }
                }
            }

            // Generate Excel
            byte[] excelBytes = ExcelUtil.generateSalesReportExcel(salesList);

            // Get current date and format it as a string
            String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Set response headers
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"sales-report-" + currentDate + ".xlsx\"");

            // Write Excel bytes to response output stream
            response.getOutputStream().write(excelBytes);
            response.getOutputStream().flush();
        }
    }

}
