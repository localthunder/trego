import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.User
import com.splitter.splittr.ui.viewmodels.PaymentsViewModel
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.ui.viewmodels.UserViewModel
import com.splitter.splittr.utils.FormattingUtils
import com.splitter.splittr.utils.GradientBorderUtils
import com.splitter.splittr.utils.ImageUtils
import java.io.File

@Composable
fun PaymentItem(
    payment: Payment,
    paymentsViewModel: PaymentsViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel(),
    groupViewModel: GroupViewModel = viewModel(),
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var paidByUser by remember { mutableStateOf<User?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Fetch the payer's details
    LaunchedEffect(payment.paidByUserId) {
        userViewModel.loadUser(payment.paidByUserId)
    }

    // Observe the user data
    val user by userViewModel.user.collectAsState()
    val userLoading by userViewModel.loading.collectAsState()
    val userError by userViewModel.error.collectAsState()

    // Update paidByUser when user data changes
    LaunchedEffect(user) {
        paidByUser = user
    }

    // Handle user loading error
    LaunchedEffect(userError) {
        error = userError
    }

    var logoFile by remember { mutableStateOf<File?>(null) }
    var dominantColors by remember { mutableStateOf(listOf<Color>()) }

    // Load group image
    LaunchedEffect(payment.groupId) {
        groupViewModel.loadGroupDetails(payment.groupId)
    }

    val groupImage by groupViewModel.groupImage.collectAsState()

    // Process group image
    LaunchedEffect(groupImage) {
        groupImage?.let { imagePath ->
            logoFile = ImageUtils.getImageFile(context, imagePath)
            val bitmap = BitmapFactory.decodeFile(logoFile?.path)
            if (bitmap != null) {
                dominantColors = GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
                if (dominantColors.size < 2) {
                    val averageColor = Color(GradientBorderUtils.getAverageColor(bitmap))
                    dominantColors = listOf(averageColor, averageColor.copy(alpha = 0.7f))
                }
            }
        }
    }

    val borderSize = 2.dp
    val borderBrush = if (dominantColors.size >= 2) {
        Brush.linearGradient(dominantColors)
    } else {
        Brush.linearGradient(listOf(Color.Gray, Color.LightGray))
    }

    PaymentAndTransactionCard(
        logoFile = logoFile,
        nameToShow = payment.description ?: "N/A",
        amount = payment.amount,
        bookingDateTime = payment.paymentDate.toString(),
        institutionName = payment.institutionName ?: "N/A",
        paidByUser = paidByUser?.username,
        borderBrush = borderBrush,
        borderSize = borderSize,
        onClick = onClick
    )

    // Handle errors
    if (error != null) {
        Text("Error: $error", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun PaymentAndTransactionCard(
    logoFile: File?,
    nameToShow: String,
    amount: Double,
    bookingDateTime: String,
    institutionName: String,
    borderSize: Dp = 2.dp,
    borderBrush: Brush = Brush.linearGradient(listOf(Color.Gray, Color.LightGray)),
    paidByUser: String? = null,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        border = BorderStroke(borderSize, borderBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (logoFile != null) {
                val bitmap = BitmapFactory.decodeFile(logoFile.path)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Group Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = nameToShow,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                paidByUser?.let {
                    Text(
                        text = "Paid by: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = FormattingUtils.formatAmount(amount.toString()),
                    color = FormattingUtils.getAmountColor(amount.toString()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = FormattingUtils.formatDate(bookingDateTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Text(
                    text = institutionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}