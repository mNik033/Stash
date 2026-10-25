package com.stash.feature.library.mixbuilder

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.db.dao.StashMixRecipeDao
import com.stash.core.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidMixBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    recipeDao: StashMixRecipeDao,
    musicRepository: MusicRepository,
    mixWorkerScheduler: MixWorkerScheduler,
) : MixBuilderViewModel(
    savedStateHandle = savedStateHandle,
    recipeDao = recipeDao,
    musicRepository = musicRepository,
    mixWorkerScheduler = mixWorkerScheduler,
)
