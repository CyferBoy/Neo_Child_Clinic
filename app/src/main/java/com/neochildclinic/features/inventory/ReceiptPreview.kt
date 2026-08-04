package com.neochildclinic.features.inventory

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.R

/**
 * A simulation of how the A5 PDF Receipt will look.
 * Dimensions are scaled to fit a phone screen preview.
 */
@Composable
fun ReceiptPreviewContent(
    modifier: Modifier = Modifier
) {
    // Simulating an A5 paper (White background, black text)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(148f / 210f), // A5 Aspect Ratio
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .border(1.dp, Color.LightGray) // Outer border for the paper feel
                .padding(12.dp)
        ) {
            // 1. HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Side: Logo and Clinic Info side-by-side
                Row(
                    modifier = Modifier.weight(0.7f),
                    verticalAlignment = Alignment.Top
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "NeoChild Clinic",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Text(
                            text = "Old hospital road, Bengali Tola,\nSahibganj, Jharkhand 816109",
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = Color.Black
                        )
                        Text(
                            text = "Mob: 6203646653, 7033905266",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "Dr. Farogh Hassan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Right Side: Metadata
                Column(
                    modifier = Modifier.weight(0.3f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "RECEIPT",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "No: #VM-1024", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(text = "Date: 25/05/2024", fontSize = 10.sp, color = Color.Black)
                    Text(text = "Time: 11:30 AM", fontSize = 10.sp, color = Color.Black)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = Color.Black)

            // 2. PATIENT INFO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Patient: Aarav Kumar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = "Age/Sex: 2Y 4M / M", fontSize = 10.sp, color = Color.Black)
                Text(text = "Ph: 9876543210", fontSize = 10.sp, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. VACCINATION TABLE
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.Black)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Vaccine Description", modifier = Modifier.weight(1f), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = "Batch", modifier = Modifier.width(60.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = "Exp.", modifier = Modifier.width(50.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = "Amount", modifier = Modifier.width(50.dp), textAlign = TextAlign.End, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            // Data Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Hexaxim (6-in-1)", modifier = Modifier.weight(1f), fontSize = 10.sp, color = Color.Black)
                Text(text = "HX2024", modifier = Modifier.width(60.dp), fontSize = 10.sp, color = Color.Black)
                Text(text = "12/2025", modifier = Modifier.width(50.dp), fontSize = 10.sp, color = Color.Black)
                Text(text = "₹3500.00", modifier = Modifier.width(50.dp), textAlign = TextAlign.End, fontSize = 10.sp, color = Color.Black)
            }
            
            Spacer(modifier = Modifier.weight(1f))

            // 4. TOTAL SECTION
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    HorizontalDivider(modifier = Modifier.width(100.dp), thickness = 1.dp, color = Color.Black)
                    Text(
                        text = "Total Paid: ₹3500.00",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(text = "Mode: UPI", fontSize = 9.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. NEXT APPOINTMENT BOX
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Black)
                    .padding(8.dp)
            ) {
                Text(
                    text = "NEXT VACCINATION DUE ON: 15/07/2024",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
                Text(
                    text = "Recommended: Pentavalent, PCV, IPV",
                    fontSize = 9.sp,
                    color = Color.DarkGray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. FOOTER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "* Please bring this receipt on your next visit.",
                    fontSize = 8.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(0.6f)
                )
                Column(
                    modifier = Modifier.weight(0.4f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(30.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = Color.Black)
                    Text(text = "Signature / Stamp", fontSize = 8.sp, color = Color.Black)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFCCCCCC)
@Composable
fun PreviewReceipt() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        ReceiptPreviewContent()
    }
}
