package com.regula.documentreader

import android.app.AlertDialog
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.regula.documentreader.api.DocumentReader
import com.regula.documentreader.api.completions.IDocumentReaderCompletion
import com.regula.documentreader.api.completions.IDocumentReaderPrepareCompletion
import com.regula.documentreader.api.enums.DocReaderAction
import com.regula.documentreader.api.enums.eGraphicFieldType
import com.regula.documentreader.api.enums.eRFID_Password_Type
import com.regula.documentreader.api.enums.eVisualFieldType
import kotlinx.android.synthetic.main.fragment_document_scan.*

class DocumentScanFragment : Fragment() {
    companion object {
        private val where = DocumentScanFragment::class.java.name
        fun newInstance() = DocumentScanFragment()
    }

    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startDocumentScan()
    }

    private val completion =
        IDocumentReaderCompletion { action, results, error ->
            // processing is finished, all results are ready
            if (action == DocReaderAction.COMPLETE) {
                if (loadingDialog != null && loadingDialog!!.isShowing) {
                    loadingDialog!!.dismiss()
                }

                // chip scan
                // setting the chip's access key - mrz on card access number
                var accessKey: String?
                accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)
                if (accessKey != null && accessKey.isNotEmpty()) {
                    accessKey =
                        results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)
                            .replace("^", "").replace("\n", "")
                    DocumentReader.Instance().rfidScenario().setMrz(accessKey)
                    DocumentReader.Instance().rfidScenario().setPacePasswordType(
                        eRFID_Password_Type.PPT_MRZ
                    )
                } else {
                    accessKey =
                        results.getTextFieldValueByType(eVisualFieldType.FT_CARD_ACCESS_NUMBER)
                    if (accessKey != null && accessKey.isNotEmpty()) {
                        DocumentReader.Instance().rfidScenario().setPassword(accessKey)
                        DocumentReader.Instance().rfidScenario().setPacePasswordType(
                            eRFID_Password_Type.PPT_CAN
                        )
                    }
                }

                // starting chip reading
                DocumentReader.Instance()
                    .startRFIDReader(requireContext()) { rfidAction, results_RFIDReader, _ ->
                        if (rfidAction == DocReaderAction.COMPLETE) {
                            val documentImage =
                                results.getGraphicFieldImageByType(eGraphicFieldType.GF_DOCUMENT_IMAGE)
                            document_image.setImageBitmap(documentImage)
                        }
                        if (rfidAction == DocReaderAction.CANCEL) {
                            return@startRFIDReader
                        }
                        if (rfidAction == DocReaderAction.ERROR) {
                            Toast.makeText(requireContext(), "Error:$error", Toast.LENGTH_LONG).show()
                        }
                    }

            }
        }

    private fun startDocumentScan() {
        if (!DocumentReader.Instance().documentReaderIsReady) {
            val initDialog = showDialog("Initializing")
            //Reading the license from raw resource file
            try {
                val licInput = resources.openRawResource(R.raw.regula)
                val available = licInput.available()
                val license = ByteArray(available)

                licInput.read(license)

                //preparing database files, it will be downloaded from network only one time and stored on user device
                DocumentReader.Instance().prepareDatabase(
                    requireContext(),
                    "Full",
                    object : IDocumentReaderPrepareCompletion {
                        override fun onPrepareProgressChanged(progress: Int) {
                            initDialog.setTitle("Downloading database: $progress%")
                        }

                        override fun onPrepareCompleted(status: Boolean, error: String) {

                            //Initializing the reader
                            DocumentReader.Instance().initializeReader(
                                requireContext(), license
                            ) { success, error_initializeReader ->
                                if (initDialog.isShowing) {
                                    initDialog.dismiss()
                                }
                                DocumentReader.Instance().customization().edit()
                                    .setShowHelpAnimation(false).apply()
                                //initialization successful
                                if (success) {
                                    DocumentReader.Instance().processParams().scenario =
                                        "Ocr"
                                    DocumentReader.Instance()
                                        .showScanner(requireContext(), completion)
                                }
                            }
                        }
                    })

            } catch (ex: Exception) {
                ex.printStackTrace()
            }

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_document_scan, container, false)
    }

    private fun showDialog(msg: String): AlertDialog {
        val dialog = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.simple_dialog, document_scan, false)
        dialog.setTitle(msg)
        dialog.setView(dialogView)
        dialog.setCancelable(false)
        return dialog.show()
    }

}