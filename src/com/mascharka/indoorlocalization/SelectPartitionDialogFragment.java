package com.mascharka.indoorlocalization;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;


/**
 *  Copyright 2015 David Mascharka
 * 
 * This file is part of LIPS (Learning-based Indoor Positioning System).
 *
 *  LIPS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  LIPS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with LIPS.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * @author David Mascharka (david.mascharka@drake.edu)
 * 
 * Dialog to let the user indicate what partitioning they want to use
 * 
 * Loads the appropriate classifier for the number of partitions in the
 * room/building. Likely unnecessary for an application that wants to
 * just implement localization but may be helpful in determining which
 * classifier does best to implement in an enterprise-scale application
 *
 */
public class SelectPartitionDialogFragment extends DialogFragment {
	
	/* The activity that creates an instance of this dialog fragment must
	 * implement this interface in order to receive event callbacks.
	 * The method passes the building the user touched.
	 */
	public interface SelectPartitionDialogListener {
		public void onPartitionChanged(String partitioning);
	}
	
	// Use this instance of the interface to deliver action events
	SelectPartitionDialogListener listener;
	
	// Instantiate the SelectBuildingDialogListener
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		// Verify the host activity implements the callback interface
		try {
			// Instantiate the listener so we can send events to the host
			listener = (SelectPartitionDialogListener) activity;
		} catch (ClassCastException e) {
			// The activity doesn't implement the interface
			throw new ClassCastException(activity.toString() + 
				" must implement SelectPartitionDialogListener");
		}
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		
		builder.setTitle(R.string.dialog_select_partitioning)
			.setItems (R.array.partitions, new DialogInterface.OnClickListener() {	
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (which == 0) {
						listener.onPartitionChanged("Full");
					} else if (which == 1) {
						listener.onPartitionChanged("3Partition");
					} else if (which == 2) {
						listener.onPartitionChanged("5Partition");
					} else if (which == 3) {
						listener.onPartitionChanged("7Partition");
					}
				}
			});
		
		return builder.create();
	}
}