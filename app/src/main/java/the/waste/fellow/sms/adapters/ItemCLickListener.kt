package the.waste.fellow.sms.adapters

/**
 * Created by R Ankit on 25-12-2016.
 */
interface ItemCLickListener {
    fun itemClicked(color: Int, contact: String?, savedContactName: String?, id: Long, read: String?) //void itemLongClicked(int position,String contact,long id);
}