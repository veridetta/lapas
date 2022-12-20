package com.example.ppllapasfix.ui.pengajuan

import android.Manifest
import android.R.attr.path
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.example.ppllapasfix.R
import com.example.ppllapasfix.databinding.ActivityPengajuanBinding
import com.example.ppllapasfix.network.State
import com.example.ppllapasfix.ui.antrian.AntrianActivity
import com.example.ppllapasfix.ui.wargabinaandetail.WargaBinaanDetailActivity
import com.example.ppllapasfix.utils.MyPreference
import com.example.ppllapasfix.utils.getPath
import com.techiness.progressdialoglibrary.ProgressDialog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.android.ext.android.inject
import java.io.File


class PengajuanActivity : AppCompatActivity() {

    private val viewModel: PengajuanViewModel by inject()

    companion object{
        const val WARGA_BINAAN_ID = "WARGA_BINAAN_ID"
        const val NAME: String = "NAME"
//        const val STATUS = "STATUS"
        //PERMISSION request constant, assign any value
        private const val STORAGE_PERMISSION_CODE = 100
        private const val TAG = "PERMISSION_TAG"
    }

    private var pdfFile: File? = null
    private var name: String? = ""
//    var status = false

    private lateinit var progressDialog: ProgressDialog

    private lateinit var binding: ActivityPengajuanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPengajuanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        progressDialog = ProgressDialog(this)
        name = intent.getStringExtra(NAME)
//        status = intent.getBooleanExtra(PengajuanActivity.STATUS, false)

        binding.textNamaWbp.text = "Nama WBP : $name"

        binding.img.setOnClickListener {
            if (checkPermission()){
                Log.d(TAG, "onCreate: Permission already granted, create folder")
                selectPdf()
            }
            else{
                Log.d(TAG, "onCreate: Permission was not granted, request")
                requestPermission()
            }

        }

            binding.btnUnggah.setOnClickListener {
                prosesPengajuan()
            }


    }

    private fun prosesPengajuan(){
        if(pdfFile == null){
            Toast.makeText(this, "Harap upload berkas terlebih dahulu", Toast.LENGTH_LONG).show()
            return
        }

        val file = pdfFile.toMultipartBody("berkas")
        val pengunjungId = MyPreference.getUser()?.id.toString().toRequestBody("text/plain".toMediaType())
        createKunjungan(pengunjungId, file!!);
    }

    private fun selectPdf() {
        val pdfIntent = Intent(Intent.ACTION_GET_CONTENT)
        pdfIntent.type = "application/pdf"
        pdfIntent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(pdfIntent, 12)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // For loading PDF
        when (requestCode) {
            12 -> if (resultCode == RESULT_OK) {
                var pdfUri = data?.data!!
                pdfFile = File(getPath(pdfUri,this) ?: "")

                val path = getPath(pdfUri,this)
                val fileName = path?.substring(path.lastIndexOf("/")+1)
                binding.txtFile.text = fileName
            }
        }
    }

    private fun createKunjungan(pengunjungId: RequestBody, file: MultipartBody.Part){
        viewModel.createPengajuan(
            pengunjungId,
            file!!).observe(this) {
            when(it.state){
                State.SUCCESS -> {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Berhasil mengunggah file", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, AntrianActivity::class.java)
                    finish()
                }
                State.ERROR -> {
                    progressDialog.dismiss()
                    MaterialDialog(this).show {
                        title(R.string.terjadi_kesalahan)
                        message(text = it.message ?: "Terjadi kesalahan")
                        negativeButton(R.string.batal) {
                        }
                    }
                }
                State.LOADING -> {
                    progressDialog.show();
                }
            }

        }
    }

    private fun File?.toMultipartBody(name: String = "pdf"): MultipartBody.Part? {
        if (this == null) return null
        val reqFile: RequestBody = this.asRequestBody("application/pdf".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(name, this.name, reqFile)
    }
    private fun requestPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            try {
                Log.d(TAG, "requestPermission: try")
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                val uri = Uri.fromParts("package", this.packageName, null)
                intent.data = uri
                storageActivityResultLauncher.launch(intent)
            }
            catch (e: Exception){
                Log.e(TAG, "requestPermission: ", e)
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                storageActivityResultLauncher.launch(intent)
            }
        }
        else{
            //Android is below 11(R)
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
    }
    private val storageActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        Log.d(TAG, "storageActivityResultLauncher: ")
        //here we will handle the result of our intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            if (Environment.isExternalStorageManager()){
                //Manage External Storage Permission is granted
                Log.d(TAG, "storageActivityResultLauncher: Manage External Storage Permission is granted")
                selectPdf()
            }
            else{
                //Manage External Storage Permission is denied....
                Log.d(TAG, "storageActivityResultLauncher: Manage External Storage Permission is denied....")
                Toast.makeText(this,"Manage External Storage Permission is denied....", Toast.LENGTH_SHORT).show()
            }
        }
        else{
            //Android is below 11(R)
        }
    }
    private fun checkPermission(): Boolean{
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            Environment.isExternalStorageManager()
        }
        else{
            //Android is below 11(R)
            val write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE){
            if (grantResults.isNotEmpty()){
                //check each permission if granted or not
                val write = grantResults[0] == PackageManager.PERMISSION_GRANTED
                val read = grantResults[1] == PackageManager.PERMISSION_GRANTED
                if (write && read){
                    //External Storage Permission granted
                    Log.d(TAG, "onRequestPermissionsResult: External Storage Permission granted")
                    selectPdf()
                }
                else{
                    //External Storage Permission denied...
                    Log.d(TAG, "onRequestPermissionsResult: External Storage Permission denied...")
                    Toast.makeText(this,"Manage External Storage Permission is denied....", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}